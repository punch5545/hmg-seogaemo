package hmgapi.mods.wireless;
import hmgapi.hook.*;
import java.util.Arrays;
public final class CarLaneCaptureTest {
    static void check(boolean b) { if(!b) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        ModContext mod=new ModContext(); CarLaneCapture capture=new CarLaneCapture(mod);
        byte[] frame=new byte[81]; frame[0]=(byte)0xf1; frame[67]=(byte)0x80; frame[80]=(byte)0xfe;
        byte[] original=frame.clone();
        capture.record(frame);
        check(Arrays.equals(frame,original) && Observations.notes==1 && mod.logs==0);
        check(Observations.key.startsWith("source=car-nav frame=f1") && Observations.detail.length()==168
            && Observations.detail.endsWith("fe") && Observations.detail.substring(6+134,6+136).equals("80"));
        capture.record(frame);check(Observations.notes==1 && Observations.flushes==2);
        java.lang.reflect.Field clock=CarLaneCapture.class.getDeclaredField("checkedAt");clock.setAccessible(true);clock.setLong(capture,0);
        Points.dev=1;capture.record(frame);check(mod.logs==1 && mod.last.endsWith("fe"));
        capture.record(frame);check(mod.logs==1);
        frame[1]=1;capture.record(frame);check(mod.logs==1 && Observations.notes==2);
        clock.setLong(capture,0);capture.record(frame);check(mod.logs==2);
        capture.record(null);capture.record(new byte[80]);check(Observations.notes==2);
        System.out.println("ok stock lane capture: full 81 bytes, no mutation, bounded records, dedup, dev/rate gate");
    }
}
