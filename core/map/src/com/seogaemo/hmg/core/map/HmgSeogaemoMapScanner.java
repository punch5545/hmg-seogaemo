// Follows HMG-API core/runtime/java/hmgapi/engine/MapProbe.java@4fab437 (structural point semantics).
package com.seogaemo.hmg.core.map;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Resolves every catalogue point from stock DEX shapes and reference names. */
final class HmgSeogaemoMapScanner {
    static final String[] BOOT = {"core","conscrypt","okhttp","core-junit","bouncycastle","ext","framework","framework2","telephony-common","voip-common","mms-common","android.policy","services","apache-xml","webviewchromium","automotive"};
    final Map<String,Map<String,Object>> points=new TreeMap<String,Map<String,Object>>();
    final Map<String,String> missing=new TreeMap<String,String>();
    final Set<String> byReference=new java.util.TreeSet<String>();
    final Map<String,String> files=new TreeMap<String,String>();
    final Map<String,Object> appFiles=new TreeMap<String,Object>();
    private final Map<String,String> resolvedClasses=new LinkedHashMap<String,String>();
    private final List<Reference> references=new ArrayList<Reference>();
    private final Map<String,HmgSeogaemoDexIndex.Type> bootClasses=new LinkedHashMap<String,HmgSeogaemoDexIndex.Type>();
    private final String system, build;
    private List<Object> groupPaths=Collections.emptyList();
    /** One bundled map as a source of reference names. */
    private static final class Reference {
        final Map<String,String> names=new LinkedHashMap<String,String>();
        final Map<String,Object> files=new LinkedHashMap<String,Object>();
        String build="";
    }
    /** The firmware build shared by cars of one release: ro.build.product without its leading model token. */
    static String buildOf(String product) { int dot=product==null?-1:product.indexOf('.'); return dot<0?"":product.substring(dot+1); }
    HmgSeogaemoMapScanner(String system,File maps,String product) throws Exception {
        this.system=system; this.build=buildOf(product);
        for(String name:BOOT) {
            File file=new File(system+"/framework/"+name+".odex");
            if(file.isFile()) bootClasses.putAll(HmgSeogaemoDexIndex.read(file));
        }
        List<File> mapsFound=new ArrayList<File>(); collect(maps,mapsFound);
        for(File dir:mapsFound) {
            try {
                Reference reference=new Reference();
                @SuppressWarnings("unchecked") Map<String,Object> entries=(Map<String,Object>)HmgSeogaemoJsonIO.object(HmgSeogaemoMapFiles.read(new File(dir,"map.json"))).get("points");
                for(Map.Entry<String,Object> entry:entries.entrySet()) {
                    @SuppressWarnings("unchecked") Map<String,Object> value=(Map<String,Object>)entry.getValue();
                    for(String field:new String[]{"class","field","method"}) if(value.containsKey(field)) reference.names.put(entry.getKey(),String.valueOf(value.get(field)));
                }
                references.add(reference);
                // Without a readable car.json the map still supplies names, at the lowest rank.
                File carFile=new File(dir,"car.json");
                if(carFile.isFile()) {
                    Map<String,Object> car=HmgSeogaemoJsonIO.object(HmgSeogaemoMapFiles.read(carFile));
                    reference.build=buildOf(car.get("product")==null?null:String.valueOf(car.get("product")));
                    @SuppressWarnings("unchecked") Map<String,Object> hashes=(Map<String,Object>)car.get("files");
                    if(hashes!=null) reference.files.putAll(hashes);
                }
            } catch(Exception ignored) { }
        }
    }
    private static void collect(File dir,List<File> found) {
        if(!dir.isDirectory()) return;
        File[] children=dir.listFiles(); if(children==null) return; java.util.Arrays.sort(children);
        if(new File(dir,"map.json").isFile()) found.add(dir);
        for(File child:children) if(child.isDirectory()) collect(child,found);
    }
    private File stock(String path) { return new File(system+path.substring("/system".length())); }
    /**
     * Reference names of one point, nearest firmware first: bundles whose stock files of this group are
     * byte-identical to the unit's, then bundles of the same firmware build, then every other bundle.
     * Obfuscated names differ between builds, so a farther bundle is consulted only when no nearer
     * one names a matching candidate.
     */
    private List<Set<String>> referenceTiers(String id) {
        List<Set<String>> tiers=new ArrayList<Set<String>>();
        for(int i=0;i<3;i++) tiers.add(new LinkedHashSet<String>());
        for(Reference reference:references) {
            String name=reference.names.get(id); if(name==null) continue;
            boolean identical=!groupPaths.isEmpty();
            for(Object path:groupPaths) {
                String mine=files.get(String.valueOf(path));
                if(mine==null||!mine.equals(reference.files.get(String.valueOf(path)))) identical=false;
            }
            tiers.get(identical?0:build.length()>0&&build.equals(reference.build)?1:2).add(name);
        }
        return tiers;
    }
    @SuppressWarnings("unchecked")
    void scan(File catalogue) throws Exception {
        File[] groups=catalogue.listFiles(); if(groups==null) throw new IllegalStateException("catalogue missing");
        java.util.Arrays.sort(groups);
        for(File group:groups) {
            if(!group.getName().endsWith(".json")) continue;
            Map<String,Object> spec=HmgSeogaemoJsonIO.object(HmgSeogaemoMapFiles.read(group));
            List<Object> paths=(List<Object>)spec.get("files");
            Map<String,Object> af=new LinkedHashMap<String,Object>();
            af.put("package",spec.get("namespace")); af.put("files",paths); appFiles.put(String.valueOf(spec.get("group")),af);
            Map<String,HmgSeogaemoDexIndex.Type> classes=new LinkedHashMap<String,HmgSeogaemoDexIndex.Type>(bootClasses);
            boolean available=true;
            for(Object pathObject:paths) {
                String path=String.valueOf(pathObject); File file=stock(path);
                if(!file.isFile()) { available=false; continue; }
                files.put(path,HmgSeogaemoMapFiles.md5(file));
                boolean inBoot=false;
                if(spec.get("namespace")==null) for(String bootName:BOOT) if(path.equals("/system/framework/"+bootName+".odex")) inBoot=true;
                if(!inBoot) classes.putAll(HmgSeogaemoDexIndex.read(file));
            }
            groupPaths=paths;
            Map<String,Object> descriptions=(Map<String,Object>)spec.get("points");
            if(!available) { for(String id:descriptions.keySet()) missing.put(id,"stock file absent"); continue; }
            Map<String,Map<String,Object>> pending=new LinkedHashMap<String,Map<String,Object>>();
            for(Map.Entry<String,Object> entry:descriptions.entrySet()) pending.put(entry.getKey(),(Map<String,Object>)entry.getValue());
            while(!pending.isEmpty()) {
                boolean changed=false;
                for(String id:new ArrayList<String>(pending.keySet())) {
                    Map<String,Object> point=pending.get(id);
                    try {
                        Map<String,Object> found=resolvePoint(id,point,classes);
                        points.put(id,found); pending.remove(id); changed=true;
                    } catch(NeedsDependency wait) { }
                    catch(IllegalStateException fail) { missing.put(id,fail.getMessage()); pending.remove(id); changed=true; }
                }
                if(!changed) { for(String id:pending.keySet()) missing.put(id,"dependency unresolved"); break; }
            }
        }
    }
    private static final class NeedsDependency extends Exception { }
    private static List<?> list(Object value) { return value instanceof List ? (List<?>)value : Collections.emptyList(); }
    private String type(Object value) throws NeedsDependency {
        String name=String.valueOf(value);
        if(!name.startsWith("@")) return name;
        String suffix=""; while(name.endsWith("[]")) {name=name.substring(0,name.length()-2);suffix+="[]";}
        String base=resolvedClasses.get(name.substring(1));
        if(base==null) throw new NeedsDependency();
        return base+suffix;
    }
    private List<String> types(Object raw) throws NeedsDependency {
        List<String> result=new ArrayList<String>(); for(Object item:list(raw)) result.add(type(item)); return result;
    }
    @SuppressWarnings("unchecked")
    private boolean field(HmgSeogaemoDexIndex.Member member,Map<String,Object> shape) throws NeedsDependency {
        return (!shape.containsKey("name")||member.name.equals(shape.get("name"))) &&
            (!shape.containsKey("type")||member.valueType.equals(type(shape.get("type")))) &&
            (!shape.containsKey("class_member")||member.classMember==Boolean.TRUE.equals(shape.get("class_member")));
    }
    private boolean method(HmgSeogaemoDexIndex.Member member,Map<String,Object> shape) throws NeedsDependency {
        if(member.name.startsWith("<")) return false;
        if(shape.containsKey("name")&&!member.name.equals(shape.get("name"))) return false;
        if(shape.containsKey("parameters")&&!member.arguments.equals(types(shape.get("parameters")))) return false;
        if(shape.containsKey("returns")&&!member.returnType.equals(type(shape.get("returns")))) return false;
        if(shape.containsKey("class_member")&&member.classMember!=Boolean.TRUE.equals(shape.get("class_member"))) return false;
        return member.literals.containsAll(list(shape.get("literals")));
    }
    @SuppressWarnings("unchecked")
    private boolean clazz(HmgSeogaemoDexIndex.Type candidate,Map<String,Object> shape) throws NeedsDependency {
        if(shape.containsKey("name")&&!candidate.name.equals(shape.get("name"))) return false;
        if(shape.containsKey("namespace")&&!candidate.name.startsWith(shape.get("namespace")+".")) return false;
        if(shape.containsKey("parent")&&!String.valueOf(shape.get("parent")).equals(candidate.parent)) return false;
        for(Object args:list(shape.get("constructors"))) {
            boolean found=false; List<String> expected=types(args);
            for(HmgSeogaemoDexIndex.Member m:candidate.methods) if(m.name.equals("<init>")&&m.arguments.equals(expected)) found=true;
            if(!found) return false;
        }
        for(Object raw:list(shape.get("field_shapes"))) {
            boolean found=false; for(HmgSeogaemoDexIndex.Member m:candidate.fields) if(field(m,(Map<String,Object>)raw)) found=true;
            if(!found) return false;
        }
        for(Object raw:list(shape.get("method_shapes"))) {
            boolean found=false; for(HmgSeogaemoDexIndex.Member m:candidate.methods) if(method(m,(Map<String,Object>)raw)) found=true;
            if(!found) return false;
        }
        return candidate.literals.containsAll(list(shape.get("literals")));
    }
    private static Map<String,Object> entry(String key,Object value) { Map<String,Object> result=new LinkedHashMap<String,Object>();result.put(key,value);return result; }
    @SuppressWarnings("unchecked")
    private Map<String,Object> resolvePoint(String id,Map<String,Object> point,Map<String,HmgSeogaemoDexIndex.Type> classes) throws NeedsDependency {
        String kind=String.valueOf(point.get("type")); Map<String,Object> shape=(Map<String,Object>)point.get("match");
        boolean reference=shape.containsKey("smali_pattern")||shape.containsKey("smali_code")||shape.containsKey("sole_caller");
        // A non-reference point has one pass over every candidate; a reference point stops at the
        // nearest tier of names that matches anything, and more than one match there stays ambiguous.
        List<Set<String>> tiers=reference?referenceTiers(id):Collections.<Set<String>>singletonList(null);
        if(kind.equals("class")) {
            List<String> hits=new ArrayList<String>();
            for(Set<String> names:tiers) {
                for(HmgSeogaemoDexIndex.Type candidate:classes.values()) if((names==null||names.contains(candidate.name))&&clazz(candidate,shape)) hits.add(candidate.name);
                if(!hits.isEmpty()) break;
            }
            if(hits.size()!=1) throw new IllegalStateException(hits.size()+" classes match");
            resolvedClasses.put(id,hits.get(0)); if(reference) byReference.add(id);
            return entry("class",hits.get(0));
        }
        String owner=resolvedClasses.get(point.get("owner"));
        if(owner==null) throw new NeedsDependency();
        HmgSeogaemoDexIndex.Type candidate=classes.get(owner); if(candidate==null) throw new IllegalStateException("owner absent");
        if(kind.equals("ctor")) {
            List<String> expected=types(shape.get("parameters")); int hits=0;
            for(HmgSeogaemoDexIndex.Member m:candidate.methods) if(m.name.equals("<init>")&&m.arguments.equals(expected)) hits++;
            if(hits!=1) throw new IllegalStateException(hits+" constructors match");
            return entry("ctor",expected);
        }
        List<HmgSeogaemoDexIndex.Member> hits=new ArrayList<HmgSeogaemoDexIndex.Member>();
        for(Set<String> names:tiers) {
            if(kind.equals("field")) for(HmgSeogaemoDexIndex.Member m:candidate.fields) if((names==null||names.contains(m.name))&&field(m,shape)) hits.add(m);
            if(kind.equals("method")) for(HmgSeogaemoDexIndex.Member m:candidate.methods) if((names==null||names.contains(m.name))&&method(m,shape)) hits.add(m);
            if(!hits.isEmpty()) break;
        }
        if(hits.size()!=1) throw new IllegalStateException(hits.size()+" "+kind+" matches");
        if(reference) byReference.add(id);
        HmgSeogaemoDexIndex.Member m=hits.get(0); Map<String,Object> result=new LinkedHashMap<String,Object>();
        if(kind.equals("field")) {result.put("field",m.name);result.put("type",m.valueType);result.put("static",m.classMember);}
        else {result.put("method",m.name);result.put("params",m.arguments);result.put("ret",m.returnType);result.put("static",m.classMember);}
        return result;
    }
}
