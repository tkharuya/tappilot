package jp.tappilot.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Single-threaded, callback-driven runner. Android and unit tests supply the ports. */
public final class Runner {
    public enum State { STOPPED, COUNTDOWN, RUNNING, PAUSED }
    public interface Scheduler {long now();void post(Runnable r,long delay);void clear();}
    public interface Done {void finish(boolean ok);}
    public interface Port {void execute(Request r,Done done);void cancel();}
    public interface Listener {void changed();}
    public static final class Request {
        public final Plan.Step step;
        public final int count, positionJitterPx;
        public final long intervalMs;
        public Request(Plan.Step step,int count,long intervalMs,int positionJitterPx){this.step=step;this.count=count;this.intervalMs=intervalMs;this.positionJitterPx=positionJitterPx;}
    }
    private final Scheduler scheduler;
    private final Port port;
    private final Listener listener;
    private final Random random=new Random();
    private Plan plan;
    private final List<Plan.Step> enabled=new ArrayList<>();
    private State state=State.STOPPED;
    private long token,activeMs,segmentAt,readyAt,pausedDelayMs;
    private int index,withinStep;
    private boolean inFlight,resumeQueued;
    private long completedCycles,completedActions,completedTaps;
    private String message="停止中";
    public Runner(Scheduler scheduler,Port port,Listener listener){this.scheduler=scheduler;this.port=port;this.listener=listener;}
    public State state(){return state;}
    public boolean active(){return state==State.RUNNING||state==State.COUNTDOWN;}
    public String message(){return message;}
    public long cycles(){return completedCycles;}
    public long actions(){return completedActions;}
    public long taps(){return completedTaps;}
    public int stepIndex(){return index;}
    public long elapsed(){return activeMs+(state==State.RUNNING?scheduler.now()-segmentAt:0);}
    public long countdown(){return Math.max(0,readyAt-scheduler.now());}
    public double tapsPerSecond(){return elapsed()==0?0:completedTaps*1000.0/elapsed();}
    public Plan snapshot(){return plan;}
    public void start(Plan source){
        source.validate();
        if(source.steps.stream().noneMatch(s->s.enabled))throw new IllegalArgumentException("有効な操作を1件以上追加してください");
        if(inFlight)throw new IllegalArgumentException("前の操作が終了するまで少し待ってください");
        stop("停止中");plan=source.copy();enabled.clear();
        for(Plan.Step s:plan.steps)if(s.enabled)enabled.add(s);
        index=withinStep=0;completedCycles=completedActions=completedTaps=activeMs=0;
        beginCountdown(plan.startDelayMs);
    }
    private void beginCountdown(long delayMs){
        pausedDelayMs=0;
        readyAt=scheduler.now()+delayMs;
        state=State.COUNTDOWN;message="開始待ち";long id=token;listener.changed();
        scheduler.post(()->{if(id!=token||state!=State.COUNTDOWN)return;state=State.RUNNING;segmentAt=scheduler.now();message="実行中";listener.changed();armLimit(id);next(id);},delayMs);
    }
    private void armLimit(long id){
        if(plan.timeLimitMs>0)scheduler.post(()->{if(id==token&&state==State.RUNNING)stop("時間制限に到達");},Math.max(0,plan.timeLimitMs-elapsed()));
    }
    public void pause(){
        if(!active())return;
        if(state==State.COUNTDOWN)pausedDelayMs=countdown();
        if(state==State.RUNNING)activeMs+=scheduler.now()-segmentAt;
        token++;scheduler.clear();resumeQueued=false;state=State.PAUSED;message="一時停止";port.cancel();listener.changed();
    }
    public void resume(){
        if(state!=State.PAUSED)return;
        if(pausedDelayMs>0){beginCountdown(pausedDelayMs);return;}
        if(inFlight){resumeQueued=true;message="操作の終了後に再開します";listener.changed();return;}
        if(plan.timeLimitMs>0 && activeMs>=plan.timeLimitMs){stop("時間制限に到達");return;}
        resumeQueued=false;state=State.RUNNING;segmentAt=scheduler.now();message="実行中";long id=++token;listener.changed();armLimit(id);next(id);
    }
    public void stop(String reason){
        if(state==State.RUNNING)activeMs+=scheduler.now()-segmentAt;
        pausedDelayMs=0;
        token++;scheduler.clear();resumeQueued=false;state=State.STOPPED;message=reason;port.cancel();listener.changed();
    }
    private long jitter(long base){return Math.max(0,base+(plan.timingJitterMs==0?0:random.nextInt(plan.timingJitterMs*2+1)-plan.timingJitterMs));}
    private void next(long id){
        if(id!=token||state!=State.RUNNING)return;
        if(plan.timeLimitMs>0&&elapsed()>=plan.timeLimitMs){stop("時間制限に到達");return;}
        if(plan.cycles>0&&completedCycles>=plan.cycles){stop("指定回数を完了");return;}
        Plan.Step s=enabled.get(index);
        boolean across=enabled.size()==1&&s.kind==Plan.Kind.TAP&&s.repeats==1&&plan.cycleGapMs==0;
        long interval=s.kind==Plan.Kind.TAP?Math.max(s.durationMs+1,jitter(s.gapMs)):jitter(s.gapMs);
        int count=1;
        if(s.kind==Plan.Kind.TAP&&plan.fastBatch&&plan.timingJitterMs==0&&interval<60){
            int maximum=(int)Math.max(1,Math.min(10,120/interval));
            count=across?(int)Math.min(maximum,plan.cycles==0?maximum:plan.cycles-completedCycles):Math.min(maximum,s.repeats-withinStep);
        }
        final int n=count;final long sentAt=scheduler.now();
        if(s.kind==Plan.Kind.WAIT){
            scheduler.post(()->{if(id==token&&state==State.RUNNING)complete(id,s,1,false,sentAt,interval);},s.durationMs);
        } else {
            inFlight=true;
            try {
                port.execute(new Request(s,n,interval,plan.positionJitterPx),ok->{
                    inFlight=false;
                    if(id!=token||state!=State.RUNNING){if(state==State.PAUSED&&resumeQueued)resume();else listener.changed();return;}
                    if(!ok){stop("操作が中断されました。位置と対象アプリを確認してください");return;}
                    complete(id,s,n,across,sentAt,interval);
                });
            }catch(RuntimeException e){inFlight=false;stop("実行エラー: "+e.getMessage());}
        }
    }
    private void complete(long id,Plan.Step s,int n,boolean across,long sentAt,long interval){
        completedActions+=n;
        if(s.kind==Plan.Kind.TAP)completedTaps+=n;
        else if(s.kind==Plan.Kind.DOUBLE_TAP)completedTaps+=2L*n;
        boolean cycleFinished=false;
        if(across){completedCycles+=n;cycleFinished=true;}
        else {
            withinStep+=n;
            if(withinStep>=s.repeats){withinStep=0;index++;if(index==enabled.size()){index=0;completedCycles++;cycleFinished=true;}}
        }
        if(plan.cycles>0&&completedCycles>=plan.cycles){stop("指定回数を完了");return;}
        long wait=s.kind==Plan.Kind.TAP?Math.max(0,sentAt+n*interval-scheduler.now()):interval;
        if(cycleFinished)wait+=plan.cycleGapMs;
        scheduler.post(()->next(id),wait);
    }
}
