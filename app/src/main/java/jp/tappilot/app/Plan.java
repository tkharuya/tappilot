package jp.tappilot.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure Java model. Coordinates use the full display, in the range 0..1. */
public final class Plan {
    public enum Kind { TAP, DOUBLE_TAP, HOLD, SWIPE, PATH, PINCH_IN, PINCH_OUT, WAIT, BACK, HOME, RECENTS }
    public static final String[] KIND_NAMES = {"タップ / 連打", "ダブルタップ", "長押し", "スライド", "自由な軌跡", "ピンチイン", "ピンチアウト", "待機", "戻る", "ホーム", "最近のアプリ"};
    public static final class Point {
        public double x, y;
        public Point(double x, double y) { this.x=x; this.y=y; }
        public Point copy() { return new Point(x,y); }
    }
    public static final class Step {
        public Kind kind=Kind.TAP;
        public String name="";
        public boolean enabled=true;
        public double x=.5, y=.5, endX=.5, endY=.25;
        public long durationMs=1, gapMs=40;
        public int repeats=1;
        public final List<Point> points=new ArrayList<>();
        public static Step create(Kind kind) {
            Step s=new Step(); s.kind=kind;
            s.durationMs = switch(kind) { case HOLD -> 800; case SWIPE,PATH,PINCH_IN,PINCH_OUT -> 400; case WAIT -> 1000; case DOUBLE_TAP -> 40; default -> 1; };
            if(kind==Kind.PINCH_IN || kind==Kind.PINCH_OUT) {s.endX=.72; s.endY=.5;}
            if(kind==Kind.WAIT) s.gapMs=0;
            return s;
        }
        public Step copy() {
            Step s=new Step(); s.kind=kind;s.name=name;s.enabled=enabled;s.x=x;s.y=y;s.endX=endX;s.endY=endY;
            s.durationMs=durationMs;s.gapMs=gapMs;s.repeats=repeats;
            for(Point p:points)s.points.add(p.copy());
            return s;
        }
        public boolean usesCoordinates() { return switch(kind) {case WAIT,BACK,HOME,RECENTS -> false; default -> true;}; }
        public String title() { return name.trim().isEmpty()?KIND_NAMES[kind.ordinal()]:name; }
        public void validate() {
            if(kind==null)throw new IllegalArgumentException("操作の種類が不正です");
            if(name==null||name.length()>80)throw new IllegalArgumentException("操作名は80文字以内です");
            coordinate(x);coordinate(y);coordinate(endX);coordinate(endY);
            range(durationMs,1,60000,"押下 / 動作時間");range(gapMs,0,3600000,"間隔 / 待機");
            range(repeats,1,1000000,"繰り返し回数");
            if(kind==Kind.TAP && durationMs>1000)throw new IllegalArgumentException("タップの押下時間は1〜1000msです。長い操作は長押しを選んでください");
            if(kind==Kind.TAP && gapMs<durationMs+1)throw new IllegalArgumentException("タップ間隔は押下時間＋1ms以上にしてください");
            if(kind==Kind.DOUBLE_TAP && durationMs>200)throw new IllegalArgumentException("ダブルタップの間隔は1〜200msです");
            if(points.size()>2000)throw new IllegalArgumentException("軌跡の点数が多すぎます");
            for(Point p:points){coordinate(p.x);coordinate(p.y);}
            if(kind==Kind.PATH && points.size()<2)throw new IllegalArgumentException("自由な軌跡は画面で線を描いて指定してください");
            if((kind==Kind.PINCH_IN||kind==Kind.PINCH_OUT) && Math.hypot(x-endX,y-endY)<.01)
                throw new IllegalArgumentException("ピンチの中心と外側の点を離してください");
        }
    }
    public String id=UUID.randomUUID().toString(),name="マイパターン";
    public final List<Step> steps=new ArrayList<>();
    public long cycles=0, timeLimitMs=0, startDelayMs=3000, cycleGapMs=0;
    public int timingJitterMs=0, positionJitterPx=0;
    public boolean fastBatch=true, keepScreenOn=true, volumeStop=true;
    public Plan copy() {
        Plan p=new Plan();p.id=id;p.name=name;p.cycles=cycles;p.timeLimitMs=timeLimitMs;p.startDelayMs=startDelayMs;
        p.cycleGapMs=cycleGapMs;p.timingJitterMs=timingJitterMs;p.positionJitterPx=positionJitterPx;
        p.fastBatch=fastBatch;p.keepScreenOn=keepScreenOn;p.volumeStop=volumeStop;
        for(Step s:steps)p.steps.add(s.copy()); return p;
    }
    public void validate() {
        if(name==null||name.trim().isEmpty()||name.length()>80)throw new IllegalArgumentException("パターン名は1〜80文字です");
        if(steps.size()>500)throw new IllegalArgumentException("操作は500件までです");
        range(cycles,0,1000000000L,"周回数");range(timeLimitMs,0,86400000L,"時間制限");
        range(startDelayMs,0,600000,"開始待ち");range(cycleGapMs,0,3600000,"周回後待機");
        range(timingJitterMs,0,60000,"時間のばらつき");range(positionJitterPx,0,200,"位置のばらつき");
        for(Step s:steps)s.validate();
    }
    public static void coordinate(double d) { if(!Double.isFinite(d)||d<0||d>1)throw new IllegalArgumentException("座標は0〜100%です"); }
    public static void range(long v,long min,long max,String name) {if(v<min||v>max)throw new IllegalArgumentException(name+"は"+min+"〜"+max+"の範囲です");}
}
