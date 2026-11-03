#!/usr/bin/env python3
# Follows HMG-API tools/map-car.py@4fab437 (host baksmali mapping behavior).
"""Resolve the HMG-Seogaemo catalogue from pulled stock odex/jar files."""
import argparse
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parent.parent
BOOT=("core","conscrypt","okhttp","core-junit","bouncycastle","ext","framework","framework2","telephony-common","voip-common","mms-common","android.policy","services","apache-xml","webviewchromium","automotive")
PRIMITIVE=dict(zip("VZBSCIJFD",("void","boolean","byte","short","char","int","long","float","double")))


def java_type(descriptor):
    dimensions=len(descriptor)-len(descriptor.lstrip("["))
    code=descriptor[dimensions:]
    basic=PRIMITIVE.get(code)
    if basic is None: basic=code[1:-1].replace("/",".")
    return basic+"[]"*dimensions


def parameters(descriptor):
    result=[];pos=0
    while pos<len(descriptor):
        end=pos
        while descriptor[end]=="[":end+=1
        if descriptor[end]=="L":end=descriptor.index(";",end)
        result.append(java_type(descriptor[pos:end+1]));pos=end+1
    return result


@dataclass
class Method:
    name:str
    args:list
    returns:str
    static:bool
    lines:list=field(default_factory=list)
    @property
    def code(self):return "\n".join(self.lines)+"\n"


@dataclass
class ClassFacts:
    name:str
    parent:str=""
    fields:list=field(default_factory=list)
    methods:list=field(default_factory=list)
    literals:set=field(default_factory=set)
    lines:list=field(default_factory=list)
    @property
    def code(self):return "\n".join(self.lines)+"\n"


def read_smalis(directory):
    result={}
    for path in directory.rglob("*.smali"):
        current=None;method=None
        for raw in path.read_text(errors="replace").splitlines():
            line=raw.strip()
            if line.startswith(".class "):
                current=ClassFacts(java_type(line.split()[-1]))
            elif line.startswith(".super ") and current:
                current.parent=java_type(line.split()[-1])
            elif line.startswith(".field ") and current:
                words=line.split(" = ",1)[0].split()
                name,kind=words[-1].split(":",1)
                current.fields.append(dict(name=name,type=java_type(kind),static="static" in words))
            elif line.startswith(".method ") and current:
                words=line.split();signature=re.fullmatch(r"(.+)\((.*)\)(.+)",words[-1])
                if not signature:raise ValueError(f"bad method in {path}: {line}")
                method=Method(signature[1],parameters(signature[2]),java_type(signature[3]),"static" in words)
                current.methods.append(method)
            elif line==".end method":method=None
            elif line.startswith("const-string") and current:
                literal=re.search(r'"(.*)"$',line)
                if literal:current.literals.add(literal[1])
            if current and line and not line.startswith(("#",".line")):
                current.lines.append(line)
                if method:method.lines.append(line)
        if current:result[current.name]=current
    return result


def stock_file(tree,logical):return tree/logical[len("/system/"):]


class HostMapper:
    def __init__(self,tree,work,baksmali):
        self.tree=tree;self.work=work;self.baksmali=baksmali
        self.cache={};self.classes={};self.names={};self.captures={}
        for name in BOOT:
            file=tree/"framework"/(name+".odex")
            if file.is_file():self.classes.update(self.disassemble(file))

    def disassemble(self,file):
        if file in self.cache:return self.cache[file]
        output=self.work/(file.parent.name+"-"+file.name)
        if file.suffix==".jar":command=["java","-jar",str(self.baksmali),"d","-o",str(output),str(file)]
        else:command=["java","-jar",str(self.baksmali),"x","-a","18","-d",str(self.tree/"framework"),
                      "-b",":".join(name+".odex" for name in BOOT),"-o",str(output),str(file)]
        subprocess.run(command,check=True,stdout=subprocess.DEVNULL)
        self.cache[file]=read_smalis(output)
        return self.cache[file]

    def type(self,value):
        if not value.startswith("@"):return value
        base=value[1:];suffix=""
        while base.endswith("[]"):base=base[:-2];suffix+="[]"
        if base not in self.names:raise KeyError(base)
        return self.names[base]+suffix

    def capture(self,group,name,classes):
        key=(group["group"],name)
        if key in self.captures:return self.captures[key]
        rule=group["captures"][name]
        owner=classes.get(self.type(rule["within"]))
        if owner is None:raise ValueError("capture owner absent")
        matches=list(re.finditer(rule["expression"],owner.code))
        if len(matches)!=1:raise ValueError(f"capture {name}: {len(matches)} matches")
        self.captures[key]=matches[0].groupdict()
        return self.captures[key]

    def field_match(self,candidate,shape):
        return ("name" not in shape or candidate["name"]==shape["name"]) and \
            ("type" not in shape or candidate["type"]==self.type(shape["type"])) and \
            ("class_member" not in shape or candidate["static"]==shape["class_member"])

    def method_match(self,candidate,shape):
        return candidate.name not in ("<init>","<clinit>") and \
            ("name" not in shape or candidate.name==shape["name"]) and \
            ("parameters" not in shape or candidate.args==list(map(self.type,shape["parameters"]))) and \
            ("returns" not in shape or candidate.returns==self.type(shape["returns"])) and \
            ("class_member" not in shape or candidate.static==shape["class_member"]) and \
            all(('"'+literal+'"') in candidate.code for literal in shape.get("literals",[])) and \
            ("smali_code" not in shape or re.search(shape["smali_code"],candidate.code) is not None)

    def class_match(self,candidate,shape):
        if "name" in shape and candidate.name!=shape["name"]:return False
        if "namespace" in shape and not candidate.name.startswith(shape["namespace"]+"."):return False
        if "parent" in shape and candidate.parent!=shape["parent"]:return False
        for args in shape.get("constructors",[]):
            wanted=list(map(self.type,args))
            if not any(method.name=="<init>" and method.args==wanted for method in candidate.methods):return False
        for wanted in shape.get("field_shapes",[]):
            if not any(self.field_match(field,wanted) for field in candidate.fields):return False
        for wanted in shape.get("method_shapes",[]):
            if not any(self.method_match(method,wanted) for method in candidate.methods):return False
        return set(shape.get("literals",[]))<=candidate.literals

    def resolve(self,group,point_id,point,classes):
        shape=dict(point["match"]);kind=point["type"]
        if "smali_pattern" in shape:
            captures=self.capture(group,shape["smali_pattern"],classes)
            if kind=="class":
                shape["name"]=shape["namespace"]+"."+captures[shape["class_group"]].replace("/",".")
            else:shape["name"]=captures[shape["group"]]
        if kind=="class":
            candidates=[item.name for item in classes.values() if self.class_match(item,shape)]
            if len(candidates)!=1:raise ValueError(f"{len(candidates)} classes match")
            self.names[point_id]=candidates[0]
            return {"class":candidates[0]}
        owner=self.names.get(point["owner"])
        if owner is None:raise KeyError(point["owner"])
        target=classes.get(owner)
        if target is None:raise ValueError("owner class absent")
        if kind=="ctor":
            args=list(map(self.type,shape.get("parameters",[])))
            candidates=[item for item in target.methods if item.name=="<init>" and item.args==args]
            if len(candidates)!=1:raise ValueError(f"{len(candidates)} constructors match")
            return {"ctor":args}
        if kind=="field":
            candidates=[item for item in target.fields if self.field_match(item,shape)]
            if len(candidates)!=1:raise ValueError(f"{len(candidates)} fields match")
            selected=candidates[0]
            return {"field":selected["name"],"type":selected["type"],"static":selected["static"]}
        candidates=[item for item in target.methods if self.method_match(item,shape)]
        if len(candidates)!=1:raise ValueError(f"{len(candidates)} methods match")
        selected=candidates[0]
        if shape.get("sole_caller"):
            ref="L"+owner.replace(".","/")+";->"+selected.name+"("
            other=[name for name,item in classes.items() if name!=owner and ref in item.code]
            expected=self.type(group["captures"][shape["smali_pattern"]]["within"])
            if other!=[expected]:raise ValueError("method caller differs")
        return {"method":selected.name,"params":selected.args,"ret":selected.returns,"static":selected.static}

    def scan(self,groups):
        mapped={};missing={};hashes={};app_files={}
        for group in groups:
            app_files[group["group"]]={"package":group["namespace"],"files":group["files"]}
            classes=dict(self.classes);absent=[]
            for logical in group["files"]:
                source=stock_file(self.tree,logical)
                if not source.is_file():absent.append(logical);continue
                hashes[logical]=hashlib.md5(source.read_bytes()).hexdigest()
                if group["namespace"] is None and source.stem in BOOT:continue
                classes.update(self.disassemble(source))
            if absent:
                missing.update({point:"stock file absent: "+absent[0] for point in group["points"]})
                continue
            pending=dict(group["points"])
            while pending:
                advanced=False
                for point_id,rule in list(pending.items()):
                    try:entry=self.resolve(group,point_id,rule,classes)
                    except KeyError:continue
                    except ValueError as error:missing[point_id]=str(error)
                    else:mapped[point_id]=entry
                    del pending[point_id];advanced=True
                if not advanced:
                    missing.update({point:"dependency unresolved" for point in pending})
                    break
        return mapped,missing,hashes,app_files


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stock",type=Path,help="pulled /system tree containing app/ and framework/")
    parser.add_argument("--product",required=True,help="exact ro.build.product")
    parser.add_argument("--onebin",required=True,help="exact ro.mobis.onebindir")
    parser.add_argument("--android",required=True,help="Android release, e.g. 4.4.2")
    parser.add_argument("--output",required=True,type=Path,help="maps/<make>/<model>/<region>/<firmware> directory")
    parser.add_argument("--baksmali",type=Path,default=Path.home()/".local/share/smali/baksmali-2.5.2.jar")
    parser.add_argument("--strict",action="store_true",help="require every catalogue point")
    options=parser.parse_args()
    if not options.baksmali.is_file():parser.error("baksmali jar absent; pass --baksmali")
    if len(options.output.parts)<4:parser.error("--output must end in make/model/region/firmware")
    if not (options.stock/"app").is_dir() or not (options.stock/"framework").is_dir():parser.error("stock tree needs app/ and framework/")
    groups=[json.loads(path.read_text()) for path in sorted((ROOT/"core/hookpoints").glob("*.json"))]
    with tempfile.TemporaryDirectory(prefix="seogaemo-host-map-") as work:
        mapper=HostMapper(options.stock,Path(work),options.baksmali)
        mapped,missing,hashes,apps=mapper.scan(groups)
    if options.strict and missing:raise SystemExit(f"{len(missing)} points unresolved; no map written")
    car={"schema":1,"product":options.product,"onebin":options.onebin,"android":options.android,
         "files":hashes,"app_files":apps,"missing":missing,"by_reference":[]}
    mapping={"schema":1,"points":mapped}
    options.output.mkdir(parents=True,exist_ok=True)
    for name,data in (("car.json",car),("map.json",mapping)):
        (options.output/name).write_text(json.dumps(data,sort_keys=True,indent=2,ensure_ascii=False)+"\n")
    print(f"mapped {len(mapped)} points; {len(missing)} unresolved; wrote {options.output}")


if __name__=="__main__":main()
