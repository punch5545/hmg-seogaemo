// Follows HMG-API core/runtime/java/hmgapi/engine/CarMap.java@4fab437 (stock hash identification).
package com.seogaemo.hmg.core.map;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

final class HmgSeogaemoMapFiles {
    private HmgSeogaemoMapFiles() {}
    static String read(File file) throws Exception { return new String(HmgSeogaemoDexIndex.all(new FileInputStream(file)),"UTF-8"); }
    static void write(File file,String text) throws Exception {
        OutputStream output=new FileOutputStream(file);
        try { output.write(text.getBytes("UTF-8")); output.flush(); } finally { output.close(); }
    }
    static String md5(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("MD5");
        InputStream input=new FileInputStream(file);
        try { byte[] chunk=new byte[32768]; int count; while((count=input.read(chunk))!=-1) digest.update(chunk,0,count); }
        finally { input.close(); }
        StringBuilder result=new StringBuilder();
        for(byte value:digest.digest()) { int n=value&255; if(n<16) result.append('0'); result.append(Integer.toHexString(n)); }
        return result.toString();
    }
}
