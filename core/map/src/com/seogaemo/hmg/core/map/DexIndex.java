// Follows HMG-API core/runtime/java/hmgapi/engine/Dex.java@4fab437 (DEX facts and odex support).
package com.seogaemo.hmg.core.map;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Extracts class signatures and const-string facts from Dalvik 035 files. */
final class DexIndex {
    static final class Member {
        String name, valueType, returnType;
        List<String> arguments = new ArrayList<String>();
        boolean classMember;
        Set<String> literals = new LinkedHashSet<String>();
    }
    static final class Type {
        String name, parent;
        final List<Member> fields = new ArrayList<Member>();
        final List<Member> methods = new ArrayList<Member>();
        final Set<String> literals = new LinkedHashSet<String>();
    }
    private final byte[] bytes;
    private final int start;
    private int cursor;
    private final String[] stringCache, typeCache;
    private DexIndex(byte[] content) {
        bytes = content;
        if (content.length < 112) throw new IllegalArgumentException("short DEX");
        if (content[0]=='d' && content[1]=='e' && content[2]=='y' && content[3]=='\n') start=word(8);
        else if (content[0]=='d' && content[1]=='e' && content[2]=='x' && content[3]=='\n') start=0;
        else throw new IllegalArgumentException("invalid DEX magic");
        stringCache=new String[word(start+56)]; typeCache=new String[word(start+64)];
    }
    static Map<String,Type> read(File file) throws Exception {
        byte[] content;
        if (file.getName().endsWith(".jar") || file.getName().endsWith(".apk")) {
            ZipFile zip=new ZipFile(file);
            try { if (zip.getEntry("classes.dex")==null) return new LinkedHashMap<String,Type>(); content=all(zip.getInputStream(zip.getEntry("classes.dex"))); }
            finally { zip.close(); }
        } else content=all(new FileInputStream(file));
        return new DexIndex(content).classes();
    }
    static byte[] all(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[32768]; int count;
            while ((count=input.read(buffer))!=-1) output.write(buffer,0,count);
            return output.toByteArray();
        } finally { input.close(); }
    }
    private int octet(int at) { return bytes[at]&255; }
    private int half(int at) { return octet(at)|(octet(at+1)<<8); }
    private int word(int at) { return half(at)|(half(at+2)<<16); }
    private int header(int at) { return word(start+at); }
    private int leb() {
        int value=0, shift=0, part;
        do { part=octet(cursor++); value|=(part&127)<<shift; shift+=7; if (shift>35) throw new IllegalArgumentException("long LEB128"); } while ((part&128)!=0);
        return value;
    }
    private String string(int index) {
        if (stringCache[index]!=null) return stringCache[index];
        cursor=start+word(start+header(60)+index*4);
        leb(); StringBuilder result=new StringBuilder();
        for (;;) {
            int a=octet(cursor++); if (a==0) break;
            if (a<128) result.append((char)a);
            else if ((a&224)==192) result.append((char)(((a&31)<<6)|(octet(cursor++)&63)));
            else result.append((char)(((a&15)<<12)|((octet(cursor++)&63)<<6)|(octet(cursor++)&63)));
        }
        return stringCache[index]=result.toString();
    }
    private String type(int index) {
        if (typeCache[index]!=null) return typeCache[index];
        String descriptor=string(word(start+header(68)+index*4));
        int depth=0; while (descriptor.charAt(depth)=='[') depth++;
        String suffix=descriptor.substring(depth), name;
        switch(suffix.charAt(0)) {
            case 'V': name="void"; break; case 'Z': name="boolean"; break;
            case 'B': name="byte"; break; case 'S': name="short"; break;
            case 'C': name="char"; break; case 'I': name="int"; break;
            case 'J': name="long"; break; case 'F': name="float"; break;
            case 'D': name="double"; break;
            default: name=suffix.substring(1,suffix.length()-1).replace('/','.');
        }
        StringBuilder result=new StringBuilder(name); while(depth-->0) result.append("[]");
        return typeCache[index]=result.toString();
    }
    private Map<String,Type> classes() {
        Map<String,Type> result=new LinkedHashMap<String,Type>();
        int count=header(96), table=start+header(100);
        for(int i=0;i<count;i++) {
            int row=table+i*32; Type item=new Type(); item.name=type(word(row));
            int parent=word(row+8); item.parent=parent==-1?null:type(parent);
            int data=word(row+24); if(data!=0) members(item,start+data);
            result.put(item.name,item);
        }
        return result;
    }
    private void members(Type item,int data) {
        cursor=data; int statics=leb(),instances=leb(),direct=leb(),virtual=leb(); int index=0;
        for(int i=0;i<statics+instances;i++) {
            if(i==statics) index=0;
            index+=leb(); int flags=leb(), next=cursor; int row=start+header(84)+index*8;
            Member f=new Member(); f.valueType=type(half(row+2)); f.name=string(word(row+4)); f.classMember=(flags&8)!=0;
            item.fields.add(f); cursor=next;
        }
        index=0;
        for(int i=0;i<direct+virtual;i++) {
            if(i==direct) index=0;
            index+=leb(); int flags=leb(), code=leb(), next=cursor;
            int row=start+header(92)+index*8, signature=start+header(76)+half(row+2)*12;
            Member m=new Member(); m.name=string(word(row+4)); m.returnType=type(word(signature+4));
            int params=word(signature+8); if(params!=0) {
                int at=start+params, size=word(at);
                for(int k=0;k<size;k++) m.arguments.add(type(half(at+4+k*2)));
            }
            m.classMember=(flags&8)!=0;
            if(code!=0) literals(start+code,m.literals);
            item.literals.addAll(m.literals); item.methods.add(m); cursor=next;
        }
    }
    private void literals(int code,Set<String> output) {
        int size=word(code+12), first=code+16;
        for(int unit=0;unit<size;) {
            int at=first+unit*2, instruction=half(at), opcode=instruction&255;
            if(opcode==0 && instruction!=0) {
                if(instruction==0x0100) { unit+=4+half(at+2)*2; continue; }
                if(instruction==0x0200) { unit+=2+half(at+2)*4; continue; }
                if(instruction==0x0300) { unit+=4+(int)((((long)half(at+2))*word(at+4)+1)/2); continue; }
            }
            if(opcode==0x1a) output.add(string(half(at+2)));
            if(opcode==0x1b) output.add(string(word(at+2)));
            unit+=width(opcode);
        }
    }
    private static int width(int op) {
        if(op==0x18) return 5;
        if(op==0x03||op==0x06||op==0x09||op==0x14||op==0x17||op==0x1b||op==0x24||op==0x25||op==0x26||op==0x2a||op==0x2b||op==0x2c||op>=0x6e&&op<=0x72||op>=0x74&&op<=0x78||op>=0xee&&op<=0xf0||op>=0xf8&&op<=0xfb) return 3;
        if(op==0x02||op==0x05||op==0x08||op==0x13||op==0x15||op==0x16||op==0x19||op==0x1a||op==0x1c||op==0x1f||op==0x20||op==0x22||op==0x23||op==0x29||op>=0x2d&&op<=0x3d||op>=0x44&&op<=0x6d||op>=0x90&&op<=0xaf||op>=0xd0&&op<=0xeb||op==0xed||op>=0xf2&&op<=0xf7||op>=0xfc&&op<=0xfe) return 2;
        return 1;
    }
}
