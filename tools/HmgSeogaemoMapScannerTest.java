package com.seogaemo.hmg.core.map;

import java.io.File;
import java.util.Map;

public final class HmgSeogaemoMapScannerTest {
    public static void main(String[] args) throws Exception {
        String product=args.length>3?args[3]:"UNIT.KOR.0000.V001.001.000000";
        HmgSeogaemoMapScanner scanner=new HmgSeogaemoMapScanner(args[0],new File(args[2]),product);
        scanner.scan(new File(args[1]));
        if(args.length>4) {
            // Reference-name ranking: args[4] is the expected method of the reference point, "-" for unresolved.
            Map<String,Object> found=scanner.points.get("example.target.reference");
            String got=found==null?"-":String.valueOf(found.get("method"));
            require(got.equals(args[4]),args[5]+": expected "+args[4]+", got "+got+" ("+scanner.missing.get("example.target.reference")+")");
            require(found==null||scanner.byReference.contains("example.target.reference"),args[5]+": reference marked");
            return;
        }
        require(scanner.points.containsKey("example.target"),"class");
        require(scanner.points.containsKey("example.target.unique"),"unique method");
        require(!scanner.points.containsKey("example.target.missing"),"missing method");
        require(!scanner.points.containsKey("example.target.ambiguous"),"ambiguous method");
        require(scanner.points.containsKey("example.target.reference"),"reference method");
        require(scanner.byReference.contains("example.target.reference"),"reference marked");
        require(Boolean.TRUE.equals(scanner.points.get("example.target.handle").get("static")),"static long field");
        require("java.lang.Object".equals(scanner.points.get("example.target.lock").get("type")),"static lock field");
        require("com.example.Example".equals(scanner.points.get("example.target.as_interface").get("ret")),"resolved return reference");
        require(Boolean.FALSE.equals(scanner.points.get("example.target.add_listener").get("static")),"instance listener method");
        require(!scanner.points.containsKey("example.target.wrong_static"),"wrong static flag rejected");
        require(!scanner.points.containsKey("example.target.wrong_signature"),"wrong signature rejected");
        require(scanner.files.containsKey("/system/app/Example.odex"),"stock hash");
        Map<String,Object> json=HmgSeogaemoJsonIO.object(HmgSeogaemoJsonIO.encode(scanner.points.get("example.target.unique")));
        require("unique".equals(json.get("method")),"JSON names");
        System.out.println("map scanner synthetic DEX: unique, missing, ambiguous, reference OK");
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
