// Follows HMG-API core/runtime/java/hmgapi/engine/{MapProbe,MapCheck,CarMap}.java@4fab437 (mapping workflow).
package com.seogaemo.hmg.core.map;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fixed root app_process commands. Result files stay in root-owned scratch until published by the package API. */
public final class MapTool {
    private final File packageRoot, stateRoot, scratch;
    private final String systemRoot, product, onebin, android, boot;
    private MapTool(File installed,File state,File work) throws Exception {
        packageRoot=installed;stateRoot=state;scratch=work;
        systemRoot=environment("SG_MAP_SYSTEM","/system");
        product=fact("SG_MAP_PRODUCT","ro.build.product");
        onebin=fact("SG_MAP_ONEBIN","ro.mobis.onebindir");
        android=fact("SG_MAP_ANDROID","ro.build.version.release");
        File bootFile=new File(environment("SG_MAP_BOOT_FILE","/proc/sys/kernel/random/boot_id"));
        boot=bootFile.isFile()?MapFiles.read(bootFile).trim():"";
    }
    private static String environment(String name,String fallback) {String value=System.getenv(name);return value==null?fallback:value;}
    private static String fact(String name,String property) throws Exception {
        String injected=System.getenv(name); if(injected!=null)return injected;
        Process process=new ProcessBuilder("getprop",property).start();
        String value=new String(DexIndex.all(process.getInputStream()),"UTF-8").trim();
        if(process.waitFor()!=0)throw new IOException("getprop failed");
        return value;
    }
    private File stock(String path) {return new File(systemRoot+path.substring("/system".length()));}
    @SuppressWarnings("unchecked")
    private boolean sameCar(Map<String,Object> car) throws Exception {
        if(!product.equals(car.get("product"))||!onebin.equals(car.get("onebin"))||!android.equals(car.get("android")))return false;
        Map<String,Object> hashes=(Map<String,Object>)car.get("files");
        if(hashes==null||hashes.isEmpty())return false;
        for(Map.Entry<String,Object> item:hashes.entrySet()) {
            if(!item.getKey().matches("/system/(?:app|framework)/[A-Za-z0-9_.-]+"))return false;
            File file=stock(item.getKey());
            if(!file.isFile()||!MapFiles.md5(file).equals(item.getValue()))return false;
        }
        return true;
    }
    private static void locate(File directory,List<File> result) {
        if(!directory.isDirectory())return;
        File[] children=directory.listFiles(); if(children==null)return;java.util.Arrays.sort(children);
        if(new File(directory,"map.json").isFile()&&new File(directory,"car.json").isFile())result.add(directory);
        for(File child:children)if(child.isDirectory()&&!child.getName().equals("active")&&!child.getName().startsWith("."))locate(child,result);
    }
    private File selection() throws Exception {
        List<File> options=new ArrayList<File>();
        locate(new File(packageRoot,"maps"),options);
        locate(new File(stateRoot,"car-maps"),options);
        for(File option:options) {
            try {if(sameCar(JsonIO.object(MapFiles.read(new File(option,"car.json")))))return option;}
            catch(Exception ignored) { }
        }
        return null;
    }
    private static String hashId(Map<String,Object> car) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("MD5");
        digest.update(JsonIO.encode(car.get("files")).getBytes("UTF-8"));
        StringBuilder id=new StringBuilder();
        for(byte b:digest.digest()){int v=b&255;if(v<16)id.append('0');id.append(Integer.toHexString(v));}
        return id.toString();
    }
    private String seal(Map<String,Object> car) throws Exception {return product+"\n"+hashId(car)+"\n"+boot+"\n";}
    private File active(String filename) {return new File(stateRoot,"car-maps/active/"+filename);}
    private boolean verified(Map<String,Object> car) throws Exception {
        return !boot.isEmpty()&&active("verified").isFile()&&seal(car).equals(MapFiles.read(active("verified")))&&
            sameCar(JsonIO.object(MapFiles.read(active("car.json"))));
    }
    @SuppressWarnings("unchecked")
    private void status() throws Exception {
        File selected=selection();
        Map<String,Object> car=selected==null?null:JsonIO.object(MapFiles.read(new File(selected,"car.json")));
        Map<String,Object> map=selected==null?null:JsonIO.object(MapFiles.read(new File(selected,"map.json")));
        Map<String,Object> entries=map==null?null:(Map<String,Object>)map.get("points");
        boolean ready=car!=null&&verified(car);
        Map<String,Object> activeMap=ready?JsonIO.object(MapFiles.read(active("map.json"))):null;
        int verifiedCount=ready?((Map<String,Object>)activeMap.get("points")).size():0;
        String source=selected==null?"none":selected.getAbsolutePath().startsWith(new File(packageRoot,"maps").getAbsolutePath())?"bundled":"generated";
        System.out.println("state="+(ready?"on":selected==null?"incompatible":"off"));
        System.out.println("restart=none");
        System.out.println("detail="+(ready?"map verified":selected==null?"mapping needed":"verification needed"));
        System.out.println("info.product="+product);
        System.out.println("info.source="+source);
        System.out.println("info.mapped="+(entries==null?0:entries.size()));
        System.out.println("info.verified="+verifiedCount);
        System.out.println("info.failed="+(ready?entries.size()-verifiedCount:0));
        if(ready) {
            Map<String,Object> activeCar=JsonIO.object(MapFiles.read(active("car.json")));
            Map<String,Object> failures=(Map<String,Object>)activeCar.get("missing");
            StringBuilder ids=new StringBuilder();
            if(failures!=null)for(String key:failures.keySet()) {if(ids.length()>145)break;if(ids.length()>0)ids.append(',');ids.append(key);}
            System.out.println("info.failing_ids="+ids);
        }
    }
    @SuppressWarnings("unchecked")
    private void verify(File directory,String source) throws Exception {
        Map<String,Object> car=JsonIO.object(MapFiles.read(new File(directory,"car.json")));
        if(!sameCar(car))throw new IllegalStateException("firmware changed");
        Map<String,Object> map=JsonIO.object(MapFiles.read(new File(directory,"map.json")));
        Map<String,Map<String,Object>> entries=new TreeMap<String,Map<String,Object>>((Map<String,Map<String,Object>>)map.get("points"));
        Map<String,String> bad=MapVerifier.check(car,entries);
        Map<String,Object> missing=(Map<String,Object>)car.get("missing");
        if(missing==null){missing=new TreeMap<String,Object>();car.put("missing",missing);}
        for(Map.Entry<String,String> item:bad.entrySet()){entries.remove(item.getKey());missing.put(item.getKey(),item.getValue());}
        File required=new File(packageRoot,"hookpoints.required");
        File[] manifests=required.listFiles();
        if(manifests!=null)for(File manifest:manifests)if(manifest.isFile())for(String line:MapFiles.read(manifest).split("\n")){
            String point=line.trim(); if(!point.isEmpty()&&!point.startsWith("#")&&!entries.containsKey(point))
                throw new IllegalStateException("required point failed: "+point);
        }
        if(entries.isEmpty())throw new IllegalStateException("no verified points");
        if(!scratch.isDirectory()&&!scratch.mkdirs())throw new IOException("scratch unavailable");
        Map<String,Object> published=new LinkedHashMap<String,Object>();published.put("schema",1);published.put("points",entries);
        MapFiles.write(new File(scratch,"map.json"),JsonIO.encode(published)+"\n");
        MapFiles.write(new File(scratch,"car.json"),JsonIO.encode(car)+"\n");
        MapFiles.write(new File(scratch,"verified"),seal(car));
        MapFiles.write(new File(scratch,"hash-id"),hashId(car)+"\n");
        MapFiles.write(new File(scratch,"source"),source+"\n");
        System.out.println("state=on\nrestart=none\ndetail=map verified");
        System.out.println("info.source="+source);
        System.out.println("info.product="+product);
        System.out.println("info.mapped="+((Map<?,?>)map.get("points")).size());
        System.out.println("info.verified="+entries.size());
        System.out.println("info.failed="+bad.size());
    }
    private void run() throws Exception {
        MapScanner scanner=new MapScanner(systemRoot,new File(packageRoot,"maps"));
        scanner.scan(new File(packageRoot,"hookpoints"));
        if(!product.matches("[A-Za-z0-9._-]+"))throw new IllegalStateException("invalid product");
        String[] parts=product.split("\\.");if(parts.length<3)throw new IllegalStateException("invalid product");
        String make=parts[0].equals("NEEV")?"hyundai":"unknown";
        String model=parts[0].equals("NEEV")?"ioniq5":parts[0].toLowerCase();
        String layout="maps/"+make+"/"+model+"/"+parts[1].toLowerCase()+"/"+parts[parts.length-1];
        Map<String,Object> car=new LinkedHashMap<String,Object>();
        car.put("schema",1);car.put("product",product);car.put("onebin",onebin);car.put("android",android);
        car.put("files",scanner.files);car.put("app_files",scanner.appFiles);car.put("missing",scanner.missing);
        car.put("by_reference",new ArrayList<String>(scanner.byReference));
        Map<String,Object> map=new LinkedHashMap<String,Object>();map.put("schema",1);map.put("points",scanner.points);
        File candidate=new File(scratch,"candidate");if(!candidate.isDirectory()&&!candidate.mkdirs())throw new IOException("scratch unavailable");
        MapFiles.write(new File(candidate,"car.json"),JsonIO.encode(car)+"\n");
        MapFiles.write(new File(candidate,"map.json"),JsonIO.encode(map)+"\n");
        verify(candidate,"generated");
        MapFiles.write(new File(scratch,"layout"),layout+"\n");
    }
    public static void main(String[] args) {
        if(args.length!=4||!(args[0].equals("status")||args[0].equals("run")||args[0].equals("verify"))){System.exit(2);return;}
        try {
            MapTool tool=new MapTool(new File(args[1]),new File(args[2]),new File(args[3]));
            if(args[0].equals("status"))tool.status();
            else if(args[0].equals("run"))tool.run();
            else {
                File selected=tool.selection();if(selected==null)throw new IllegalStateException("mapping needed");
                String source=selected.getAbsolutePath().startsWith(new File(tool.packageRoot,"maps").getAbsolutePath())?"bundled":"generated";
                tool.verify(selected,source);
            }
        }catch(Throwable error){
            System.out.println("state=failed\nrestart=none\ndetail="+error.getClass().getSimpleName()+": "+error.getMessage());
            System.exit(1);
        }
    }
}
