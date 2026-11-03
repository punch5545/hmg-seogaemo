// Follows HMG-API core/runtime/java/hmgapi/engine/MapCheck.java@4fab437 (on-car reflection checks).
package com.seogaemo.hmg.core.map;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves mapped names against each stock app's class path without starting the app. */
final class HmgSeogaemoMapVerifier {
    private HmgSeogaemoMapVerifier() {}
    @SuppressWarnings("unchecked")
    static Map<String,String> check(Map<String,Object> car,Map<String,Map<String,Object>> points) throws Exception {
        Map<String,String> failures=new LinkedHashMap<String,String>();
        Map<String,Object> appFiles=(Map<String,Object>)car.get("app_files");
        Map<String,ClassLoader> loaders=new LinkedHashMap<String,ClassLoader>();
        ClassLoader boot=ClassLoader.getSystemClassLoader().getParent();
        Constructor<?> newLoader=Class.forName("dalvik.system.PathClassLoader").getConstructor(String.class,ClassLoader.class);
        for(Map.Entry<String,Map<String,Object>> item:points.entrySet()) {
            String id=item.getKey(), group=id.substring(0,id.indexOf('.'));
            try {
                ClassLoader loader=loaders.get(group);
                if(loader==null) {
                    Map<String,Object> groupFiles=(Map<String,Object>)appFiles.get(group);
                    if(groupFiles==null) throw new IllegalStateException("group absent");
                    StringBuilder path=new StringBuilder();
                    boolean framework=groupFiles.get("package")==null;
                    String bcp=":"+System.getenv("BOOTCLASSPATH")+":";
                    for(Object value:(List<Object>)groupFiles.get("files")) {
                        String file=String.valueOf(value);
                        String code=file.replaceAll("\\.odex$",file.startsWith("/system/app/")?".apk":".jar");
                        if(framework&&bcp.contains(":"+code+":")) continue;
                        if(path.length()>0) path.append(':'); path.append(code);
                    }
                    loader=path.length()==0?boot:(ClassLoader)newLoader.newInstance(path.toString(),boot);
                    loaders.put(group,loader);
                }
                resolve(loader,id,item.getValue(),points);
            } catch(Throwable error) { failures.put(id,error.getClass().getSimpleName()); }
        }
        return failures;
    }
    private static void resolve(ClassLoader loader,String id,Map<String,Object> entry,Map<String,Map<String,Object>> points) throws Exception {
        if(entry.containsKey("class")) {Class.forName(String.valueOf(entry.get("class")),false,loader);return;}
        String owner=id.substring(0,id.lastIndexOf('.'));
        Map<String,Object> owning=points.get(owner);
        if(owning==null||!owning.containsKey("class")) throw new IllegalStateException("owner unmapped");
        Class<?> type=Class.forName(String.valueOf(owning.get("class")),false,loader);
        if(entry.containsKey("field")) {
            Field field=type.getDeclaredField(String.valueOf(entry.get("field")));
            if(field.getType()!=signature(loader,String.valueOf(entry.get("type")))||Modifier.isStatic(field.getModifiers())!=Boolean.TRUE.equals(entry.get("static"))) throw new IllegalStateException("field signature");
        } else if(entry.containsKey("method")) {
            Method method=type.getDeclaredMethod(String.valueOf(entry.get("method")),arguments(loader,entry.get("params")));
            if(method.getReturnType()!=signature(loader,String.valueOf(entry.get("ret")))||Modifier.isStatic(method.getModifiers())!=Boolean.TRUE.equals(entry.get("static"))) throw new IllegalStateException("method signature");
        } else if(entry.containsKey("ctor")) type.getDeclaredConstructor(arguments(loader,entry.get("ctor")));
        else throw new IllegalStateException("point kind");
    }
    private static Class<?>[] arguments(ClassLoader loader,Object raw) throws Exception {
        List<?> names=(List<?>)raw; Class<?>[] result=new Class<?>[names.size()];
        for(int i=0;i<result.length;i++) result[i]=signature(loader,String.valueOf(names.get(i)));
        return result;
    }
    private static Class<?> signature(ClassLoader loader,String name) throws Exception {
        if(name.endsWith("[]")) return Array.newInstance(signature(loader,name.substring(0,name.length()-2)),0).getClass();
        if(name.equals("void")) return void.class;
        if(name.equals("boolean")) return boolean.class;
        if(name.equals("byte")) return byte.class;
        if(name.equals("short")) return short.class;
        if(name.equals("char")) return char.class;
        if(name.equals("int")) return int.class;
        if(name.equals("long")) return long.class;
        if(name.equals("float")) return float.class;
        if(name.equals("double")) return double.class;
        return Class.forName(name,false,loader);
    }
}
