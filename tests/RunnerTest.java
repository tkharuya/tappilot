package jp.tappilot.app;

import java.util.*;

/** Deterministic host tests. No Android device or third-party test library required. */
public final class RunnerTest {
    static int passed;
    static final class World {
        long now,order;final PriorityQueue<Task> q=new PriorityQueue<>(Comparator.comparingLong((Task t)->t.at).thenComparingLong(t->t.order));
        static final class Task {long at,order;String owner;Runnable run;Task(long at,long order,String owner,Runnable run){this.at=at;this.order=order;this.owner=owner;this.run=run;}}
        void post(String owner,Runnable r,long delay){q.add(new Task(now+Math.max(0,delay),order++,owner,r));}
        void advance(long millis){long end=now+millis;int steps=0;while(!q.isEmpty()&&q.peek().at<=end){if(++steps>100000)throw new AssertionError("scheduler did not yield");Task t=q.poll();now=t.at;t.run.run();}now=end;}
    }
    static final class FakePort implements Runner.Port {
        final World w;final List<Runner.Request> requests=new ArrayList<>();boolean busy,cancel;int cancellations;
        FakePort(World w){this.w=w;}
        public void execute(Runner.Request r,Runner.Done done){if(busy)throw new AssertionError("overlapping dispatch");requests.add(r);busy=true;cancel=false;
            long span=r.step.kind==Plan.Kind.TAP?(r.count-1)*r.intervalMs+r.step.durationMs:r.step.durationMs;
            w.post("port",()->{busy=false;done.finish(!cancel);},Math.max(1,span));}
        public void cancel(){if(busy){cancel=true;cancellations++;}}
    }
    static final class Rig {final World w=new World();final FakePort p=new FakePort(w);final Runner r=new Runner(new Runner.Scheduler(){public long now(){return w.now;}public void post(Runnable r,long delay){w.post("runner",r,delay);}public void clear(){w.q.removeIf(t->t.owner.equals("runner"));}},p,()->{});}
    static Plan tap(long cycles,int repeats,long interval){Plan p=new Plan();p.startDelayMs=0;p.cycles=cycles;Plan.Step s=Plan.Step.create(Plan.Kind.TAP);s.gapMs=interval;s.repeats=repeats;p.steps.add(s);return p;}
    static void eq(long want,long actual){if(want!=actual)throw new AssertionError("wanted "+want+", got "+actual);}
    static void check(boolean b){if(!b)throw new AssertionError("condition failed");}
    static void test(String name,Runnable body){body.run();System.out.println("PASS "+name);passed++;}
    static void invalid(Runnable r){try{r.run();throw new AssertionError("expected validation error");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        test("single tap finite cycles batches exactly",()->{Rig x=new Rig();x.r.start(tap(23,1,10));x.w.advance(1000);eq(23,x.r.taps());eq(23,x.r.cycles());eq(3,x.p.requests.size());check(x.r.state()==Runner.State.STOPPED);});
        test("per-step repeats and cycles multiply",()->{Rig x=new Rig();x.r.start(tap(3,7,10));x.w.advance(2000);eq(21,x.r.taps());eq(3,x.r.cycles());});
        test("multiple locations retain order",()->{Rig x=new Rig();Plan p=tap(2,1,40);Plan.Step b=Plan.Step.create(Plan.Kind.TAP);b.x=.7;b.gapMs=40;p.steps.add(b);x.r.start(p);x.w.advance(1000);eq(4,x.r.taps());eq(4,x.p.requests.size());check(x.p.requests.get(0).step.x==.5);check(x.p.requests.get(1).step.x==.7);});
        test("disabled steps skipped",()->{Rig x=new Rig();Plan p=tap(2,1,20);Plan.Step b=Plan.Step.create(Plan.Kind.HOME);b.enabled=false;p.steps.add(b);x.r.start(p);x.w.advance(1000);eq(2,x.r.taps());check(x.p.requests.stream().allMatch(r->r.step.kind==Plan.Kind.TAP));});
        test("source edits cannot mutate running snapshot",()->{Rig x=new Rig();Plan p=tap(4,1,100);x.r.start(p);p.steps.get(0).x=.9;p.cycles=999;x.w.advance(1000);eq(4,x.r.cycles());check(x.p.requests.get(0).step.x==.5);});
        test("stop cancels countdown without dispatch",()->{Rig x=new Rig();Plan p=tap(0,1,10);p.startDelayMs=3000;x.r.start(p);x.w.advance(2999);x.r.stop("user");x.w.advance(10000);eq(0,x.p.requests.size());check(x.r.message().equals("user"));});
        test("resuming a countdown waits for its remaining delay",()->{
            Rig x=new Rig();Plan p=tap(1,1,10);p.startDelayMs=3000;
            x.r.start(p);x.w.advance(1000);x.r.pause();x.w.advance(5000);x.r.resume();
            check(x.r.state()==Runner.State.COUNTDOWN);eq(2000,x.r.countdown());
            x.w.advance(1999);eq(0,x.p.requests.size());x.w.advance(1);eq(1,x.p.requests.size());
        });
        test("time limit interrupts waits",()->{Rig x=new Rig();Plan p=new Plan();p.startDelayMs=0;p.timeLimitMs=50;p.steps.add(Plan.Step.create(Plan.Kind.WAIT));x.r.start(p);x.w.advance(100);check(x.r.state()==Runner.State.STOPPED);eq(50,x.r.elapsed());eq(0,x.r.cycles());});
        test("stop in flight never dispatches another gesture",()->{Rig x=new Rig();x.r.start(tap(0,1,10));x.w.advance(20);x.r.stop("user");x.w.advance(1000);eq(1,x.p.requests.size());eq(0,x.r.taps());check(x.p.cancellations>0);});
        test("pause and resume do not overlap old gesture",()->{Rig x=new Rig();x.r.start(tap(3,1,10));x.w.advance(5);x.r.pause();x.r.resume();check(x.r.state()==Runner.State.PAUSED);x.w.advance(200);x.r.resume();x.w.advance(1000);eq(3,x.r.cycles());eq(3,x.r.taps());});
        test("queued resume begins after touch release",()->{Rig x=new Rig();x.r.start(tap(4,1,10));x.w.advance(5);x.r.pause();x.r.resume();x.w.advance(1000);eq(4,x.r.taps());eq(4,x.r.cycles());check(x.r.state()==Runner.State.STOPPED);});
        test("stop cancels a queued resume",()->{Rig x=new Rig();x.r.start(tap(0,1,10));x.w.advance(5);x.r.pause();x.r.resume();x.r.stop("user");x.w.advance(1000);eq(1,x.p.requests.size());check(x.r.state()==Runner.State.STOPPED);});
        test("new start rejected until cancelled gesture finishes",()->{Rig x=new Rig();x.r.start(tap(0,1,10));x.w.advance(1);x.r.stop("user");invalid(()->x.r.start(tap(1,1,10)));x.w.advance(200);x.r.start(tap(1,1,10));x.w.advance(100);eq(1,x.r.taps());});
        test("paused time excluded from time limit",()->{Rig x=new Rig();Plan p=tap(0,1,100);p.fastBatch=false;p.timeLimitMs=200;x.r.start(p);x.w.advance(50);x.r.pause();x.w.advance(5000);x.r.resume();x.w.advance(149);check(x.r.state()==Runner.State.RUNNING);x.w.advance(2);check(x.r.state()==Runner.State.STOPPED);eq(200,x.r.elapsed());});
        test("batch mode can be disabled",()->{Rig x=new Rig();Plan p=tap(8,1,2);p.fastBatch=false;x.r.start(p);x.w.advance(100);eq(8,x.p.requests.size());check(x.p.requests.stream().allMatch(r->r.count==1));});
        test("cycle delay respected",()->{Rig x=new Rig();Plan p=tap(3,1,10);p.cycleGapMs=100;x.r.start(p);x.w.advance(1000);eq(3,x.p.requests.size());eq(3,x.r.cycles());eq(221,x.r.elapsed());});
        test("wait participates in ordered sequences",()->{Rig x=new Rig();Plan p=tap(1,1,10);Plan.Step wait=Plan.Step.create(Plan.Kind.WAIT);wait.durationMs=100;p.steps.add(0,wait);x.r.start(p);x.w.advance(99);eq(0,x.p.requests.size());x.w.advance(2);eq(1,x.r.taps());eq(1,x.r.cycles());});
        test("double tap counts two taps",()->{Rig x=new Rig();Plan p=new Plan();p.startDelayMs=0;p.cycles=2;p.steps.add(Plan.Step.create(Plan.Kind.DOUBLE_TAP));x.r.start(p);x.w.advance(1000);eq(4,x.r.taps());eq(2,x.r.actions());});
        test("no enabled actions rejected",()->{Rig x=new Rig();Plan p=tap(1,1,10);p.steps.get(0).enabled=false;invalid(()->x.r.start(p));});
        test("invalid bounds rejected",()->{Plan p=tap(1,1,10);p.steps.get(0).x=Double.NaN;invalid(p::validate);p.steps.get(0).x=.5;p.steps.get(0).gapMs=1;invalid(p::validate);p.steps.get(0).gapMs=10;p.cycles=-1;invalid(p::validate);});
        test("deep path copy remains independent",()->{Plan p=tap(1,1,10);Plan.Step s=Plan.Step.create(Plan.Kind.PATH);s.points.add(new Plan.Point(.3,.4));s.points.add(new Plan.Point(.5,.6));p.steps.add(s);Plan copy=p.copy();p.steps.get(1).points.get(0).x=.9;check(copy.steps.get(1).points.get(0).x==.3);copy.validate();});
        System.out.println(passed+" tests passed");
    }
}
