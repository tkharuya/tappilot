package jp.tappilot.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Runs only on a disposable test emulator. Not included in the application APK. */
public final class DeviceSmokeTest extends Instrumentation {
    private Activity activity;
    private View area;
    private int passed;
    private UiAutomation automation;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void report(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
    private void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private void main(Runnable r){runOnMainSync(r);}
    private <T> T read(java.util.function.Supplier<T> fn){AtomicReference<T> ref=new AtomicReference<>();main(()->ref.set(fn.get()));return ref.get();}
    private void await(BooleanSupplier fn,long limit,String why) throws Exception {long until=SystemClock.uptimeMillis()+limit;while(SystemClock.uptimeMillis()<until){if(fn.getAsBoolean())return;Thread.sleep(80);}throw new AssertionError("Timeout: "+why);}
    private void pass(String label){passed++;report("PASS "+label);}
    private View find(View v,String suffix){if(v.getClass().getName().endsWith(suffix))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View r=find(g.getChildAt(i),suffix);if(r!=null)return r;}return null;}
    private int count(String name){return read(()->{try{Field f=area.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(area);}catch(Exception e){throw new RuntimeException(e);}});}
    private float zoom(){return read(()->{try{Field f=area.getClass().getDeclaredField("zoom");f.setAccessible(true);return f.getFloat(area);}catch(Exception e){throw new RuntimeException(e);}});}
    private Plan plan(Plan.Kind kind,long cycles){Plan p=new Plan();p.name="Emulator QA "+kind;p.cycles=cycles;p.startDelayMs=0;Plan.Step a=Plan.Step.create(kind);a.x=.5;a.y=.72;a.endX=.72;a.endY=.72;a.gapMs=40;p.steps.add(a);return p;}
    private void play(Plan p){main(()->{TapService s=TapService.instance;s.store.save(p);s.showPanel();s.overlay.refreshMarkers();s.play();});}
    private void stopped() throws Exception{await(()->read(()->TapService.instance.runner.state()==Runner.State.STOPPED),30000,"runner stops");}
    private void screenshot(String name) throws Exception {waitForIdleSync();Bitmap b=automation.takeScreenshot();check(b!=null,"screenshot");File dir=getTargetContext().getExternalFilesDir(null);try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        main(()->new PlanStore(getTargetContext()).consent(true));
        activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->read(()->TapService.instance!=null&&TapService.instance.overlay!=null),30000,"accessibility connection");
        waitForIdleSync();Thread.sleep(600);screenshot("qa-home.png");pass("main screen and accessibility connection");
        activity=startActivitySync(new Intent(getTargetContext(),PlaygroundActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        area=read(()->find(activity.getWindow().getDecorView(),"$TouchArea"));check(area!=null,"test area visible");
        main(()->TapService.instance.showPanel());Thread.sleep(300);
        int before=count("taps");Plan taps=plan(Plan.Kind.TAP,20);taps.steps.get(0).durationMs=5;play(taps);stopped();
        check(count("taps")-before==20,"20 taps reach actual view: "+(count("taps")-before));check(read(()->TapService.instance.runner.taps())==20,"runner count");pass("20 batched taps reach Android view exactly");
        Plan repeat=plan(Plan.Kind.TAP,0);repeat.fastBatch=false;repeat.steps.get(0).gapMs=100;play(repeat);
        await(()->read(()->TapService.instance.runner.taps()>=3),15000,"repeat starts");main(()->TapService.instance.runner.pause());long n=read(()->TapService.instance.runner.taps());Thread.sleep(350);check(read(()->TapService.instance.runner.taps())==n,"paused count remains fixed");
        main(()->TapService.instance.runner.resume());await(()->read(()->TapService.instance.runner.taps()>=n+3),15000,"resume works");main(()->TapService.instance.stop());Thread.sleep(400);long end=read(()->TapService.instance.runner.taps());Thread.sleep(300);check(read(()->TapService.instance.runner.taps())==end,"stop remains stopped");pass("pause, resume, and stop on device");
        int swipes=count("swipes");Plan swipe=plan(Plan.Kind.SWIPE,1);swipe.steps.get(0).x=.3;swipe.steps.get(0).endX=.75;swipe.steps.get(0).durationMs=650;play(swipe);stopped();check(count("swipes")==swipes+1,"segmented swipe remains one continuous touch");pass("650ms continuous swipe across gesture chunks");
        int holds=count("holds");Plan hold=plan(Plan.Kind.HOLD,1);hold.steps.get(0).durationMs=900;play(hold);stopped();check(count("holds")==holds+1,"one long press");pass("900ms continuous long press");
        Plan longHold=plan(Plan.Kind.HOLD,1);longHold.steps.get(0).durationMs=5000;play(longHold);Thread.sleep(200);main(()->TapService.instance.stop());Thread.sleep(500);
        Plan probe=plan(Plan.Kind.TAP,1);play(probe);stopped();check(read(()->TapService.instance.runner.taps())==1,"cancel releases touch for next run");pass("cancel releases a long press before its full duration");
        float z=zoom();Plan pinch=plan(Plan.Kind.PINCH_OUT,1);pinch.steps.get(0).durationMs=700;play(pinch);stopped();check(zoom()>z*1.15,"pinch scale increases: "+z+" to "+zoom());pass("two-finger pinch out");
        Plan path=plan(Plan.Kind.PATH,1);Plan.Step line=path.steps.get(0);line.points.add(new Plan.Point(.3,.7));line.points.add(new Plan.Point(.45,.8));line.points.add(new Plan.Point(.7,.7));line.x=.3;line.y=.7;line.endX=.7;line.endY=.7;line.durationMs=600;
        int oldSwipes=count("swipes");play(path);stopped();check(count("swipes")==oldSwipes+1,"free path drag");pass("freeform path stays continuous");
        String json=PlanCodec.exportPlan(path);Plan imported=PlanCodec.importPlan(json);check(imported.steps.get(0).points.size()==3,"Android JSON roundtrip");check(!imported.id.equals(path.id),"import new identity");pass("JSON export and import with Android runtime");
        Plan marker=plan(Plan.Kind.TAP,1);main(()->{TapService.instance.store.save(marker);TapService.instance.overlay.refreshMarkers();});Thread.sleep(400);
        int w=read(()->TapService.instance.width()),h=read(()->TapService.instance.height());float x=.5f*(w-1),y=.72f*(h-1),tx=.62f*(w-1),ty=.76f*(h-1);long down=SystemClock.uptimeMillis();
        for(int i=0;i<=6;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==6?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+(tx-x)*i/6,y+(ty-y)*i/6,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);automation.injectInputEvent(e,true);e.recycle();Thread.sleep(45);}
        check(Math.abs(read(()->TapService.instance.store.current().steps.get(0).x)-.62)<.02,"marker drag updates stored x");pass("overlay marker drag saves screen coordinates");
        main(()->TapService.instance.overlay.edit(0));Thread.sleep(300);automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);pass("overlay edit dialog opens");
        screenshot("qa-playground.png");main(()->{TapService.instance.stop();TapService.instance.overlay.hide();});
        result.putString("stream","OK: "+passed+" Android smoke tests passed\n");result.putInt("passed",passed);finish(Activity.RESULT_OK,result);
    }catch(Throwable e){StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));result.putString("stream","FAILED after "+passed+" tests\n"+sw);try{if(automation!=null)screenshot("qa-failed.png");}catch(Exception ignored){}finish(Activity.RESULT_CANCELED,result);}}
}
