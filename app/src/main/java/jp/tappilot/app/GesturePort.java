package jp.tappilot.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Never queues a second dispatch until the first completes. Long strokes are released on cancel. */
public final class GesturePort implements Runner.Port {
    public interface Screen {int width();int height();}
    private final AccessibilityService service;
    private final Handler handler;
    private final Screen screen;
    private final Random random=new Random();
    private Job job;
    private static final long SLICE_MS=120;
    public GesturePort(AccessibilityService service,Handler handler,Screen screen){this.service=service;this.handler=handler;this.screen=screen;}
    @Override public void execute(Runner.Request r,Runner.Done done){
        if(job!=null)throw new IllegalStateException("前のジェスチャーが残っています");
        KeyguardManager k=(KeyguardManager)service.getSystemService(android.content.Context.KEYGUARD_SERVICE);
        if(k!=null&&k.isKeyguardLocked()){done.finish(false);return;}
        Plan.Step s=r.step;
        if(s.kind==Plan.Kind.BACK||s.kind==Plan.Kind.HOME||s.kind==Plan.Kind.RECENTS){
            int action=s.kind==Plan.Kind.BACK?AccessibilityService.GLOBAL_ACTION_BACK:s.kind==Plan.Kind.HOME?AccessibilityService.GLOBAL_ACTION_HOME:AccessibilityService.GLOBAL_ACTION_RECENTS;
            boolean ok=service.performGlobalAction(action);handler.post(()->done.finish(ok));return;
        }
        Job j=new Job();j.done=done;j.duration=s.durationMs;
        if(s.kind==Plan.Kind.TAP){
            j.segmented=false;j.burst=new GestureDescription.Builder();
            if(r.count>GestureDescription.getMaxStrokeCount())throw new IllegalArgumentException("タップ数が端末の上限を超えています");
            for(int i=0;i<r.count;i++)j.burst.addStroke(new GestureDescription.StrokeDescription(point(s.x,s.y,r.positionJitterPx),i*r.intervalMs,s.durationMs));
        }else if(s.kind==Plan.Kind.DOUBLE_TAP){
            j.segmented=false;j.burst=new GestureDescription.Builder();Path p=point(s.x,s.y,r.positionJitterPx);
            j.burst.addStroke(new GestureDescription.StrokeDescription(p,0,1));j.burst.addStroke(new GestureDescription.StrokeDescription(p,Math.max(2,s.durationMs),1));
        }else{
            j.segmented=true;
            if(s.kind==Plan.Kind.HOLD)j.paths.add(point(s.x,s.y,r.positionJitterPx));
            else if(s.kind==Plan.Kind.SWIPE){Path p=new Path();p.moveTo(px(s.x),py(s.y));p.lineTo(px(s.endX),py(s.endY));j.paths.add(p);}
            else if(s.kind==Plan.Kind.PATH){Path p=new Path();Plan.Point first=s.points.get(0);p.moveTo(px(first.x),py(first.y));for(int i=1;i<s.points.size();i++){Plan.Point v=s.points.get(i);p.lineTo(px(v.x),py(v.y));}j.paths.add(p);}
            else if(s.kind==Plan.Kind.PINCH_IN||s.kind==Plan.Kind.PINCH_OUT){
                float cx=px(s.x),cy=py(s.y),dx=px(s.endX)-cx,dy=py(s.endY)-cy;
                float factor=1;
                if(Math.abs(dx)>0)factor=Math.min(factor,Math.min(cx,screen.width()-1-cx)/Math.abs(dx));
                if(Math.abs(dy)>0)factor=Math.min(factor,Math.min(cy,screen.height()-1-cy)/Math.abs(dy));
                dx*=factor;dy*=factor;
                if(Math.hypot(dx,dy)<5)throw new IllegalArgumentException("ピンチの中心を画面の端から離してください");
                boolean inward=s.kind==Plan.Kind.PINCH_IN;
                for(int sign:new int[]{-1,1}){Path p=new Path();p.moveTo(cx+sign*dx*(inward?1:.25f),cy+sign*dy*(inward?1:.25f));p.lineTo(cx+sign*dx*(inward?.25f:1),cy+sign*dy*(inward?.25f:1));j.paths.add(p);}
            }else throw new IllegalArgumentException("未対応の操作です");
            for(Path p:j.paths){PathMeasure m=new PathMeasure(p,false);j.measures.add(m);}
        }
        job=j;dispatch(j);
    }
    private float px(double x){return (float)(x*(screen.width()-1));}
    private float py(double y){return (float)(y*(screen.height()-1));}
    private Path point(double x,double y,int jitter){
        float a=px(x),b=py(y);if(jitter>0){a+=random.nextInt(2*jitter+1)-jitter;b+=random.nextInt(2*jitter+1)-jitter;}
        a=Math.max(0,Math.min(screen.width()-1,a));b=Math.max(0,Math.min(screen.height()-1,b));Path p=new Path();p.moveTo(a,b);return p;
    }
    private Path slice(Job j,int i,long from,long to){
        PathMeasure m=j.measures.get(i);Path p=new Path();
        if(m.getLength()==0){float[] xy=new float[2];android.graphics.RectF bounds=new android.graphics.RectF();j.paths.get(i).computeBounds(bounds,true);xy[0]=bounds.left;xy[1]=bounds.top;p.moveTo(xy[0],xy[1]);}
        else{m.getSegment(m.getLength()*from/j.duration,m.getLength()*to/j.duration,p,true);}
        return p;
    }
    private void dispatch(Job j){
        if(job!=j)return;
        GestureDescription gesture;
        try{
            if(!j.segmented){j.continuing=false;gesture=j.burst.build();}
            else{
                long to=Math.min(j.duration,j.offset+SLICE_MS);j.continuing=to<j.duration;
                GestureDescription.Builder b=new GestureDescription.Builder();
                GestureDescription.StrokeDescription[] strokes=new GestureDescription.StrokeDescription[j.paths.size()];
                for(int i=0;i<strokes.length;i++){
                    Path p=slice(j,i,j.offset,to);
                    strokes[i]=j.previous==null?new GestureDescription.StrokeDescription(p,0,to-j.offset,j.continuing):j.previous[i].continueStroke(p,0,to-j.offset,j.continuing);
                    b.addStroke(strokes[i]);
                }
                j.previous=strokes;j.offset=to;gesture=b.build();
            }
            boolean accepted=service.dispatchGesture(gesture,new AccessibilityService.GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){
                    if(job!=j)return;
                    if(j.cancelled){if(j.continuing)release(j);else finish(j,false);}
                    else if(j.continuing)dispatch(j);else finish(j,true);
                }
                @Override public void onCancelled(GestureDescription g){finish(j,false);}
            },handler);
            if(!accepted)finish(j,false);
        }catch(RuntimeException e){finish(j,false);}
    }
    private void release(Job j){
        try{
            GestureDescription.Builder b=new GestureDescription.Builder();
            for(int i=0;i<j.paths.size();i++){
                float[] xy=new float[2];PathMeasure m=j.measures.get(i);
                if(m.getLength()>0)m.getPosTan(m.getLength()*j.offset/j.duration,xy,null);
                else {android.graphics.RectF bounds=new android.graphics.RectF();j.paths.get(i).computeBounds(bounds,true);xy[0]=bounds.left;xy[1]=bounds.top;}
                Path p=new Path();p.moveTo(xy[0],xy[1]);b.addStroke(j.previous[i].continueStroke(p,0,1,false));
            }
            boolean accepted=service.dispatchGesture(b.build(),new AccessibilityService.GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){finish(j,false);}
                @Override public void onCancelled(GestureDescription g){finish(j,false);}
            },handler);
            if(!accepted)finish(j,false);
        }catch(RuntimeException e){finish(j,false);}
    }
    private void finish(Job j,boolean success){if(job!=j)return;job=null;j.done.finish(success);}
    @Override public void cancel(){if(job!=null)job.cancelled=true;}
    private static final class Job {
        Runner.Done done;long duration,offset;boolean segmented,continuing,cancelled;
        GestureDescription.Builder burst;
        final List<Path> paths=new ArrayList<>();final List<PathMeasure> measures=new ArrayList<>();
        GestureDescription.StrokeDescription[] previous;
    }
}
