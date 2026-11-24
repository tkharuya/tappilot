package jp.tappilot.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PlanCodec {
    private PlanCodec() {}
    public static JSONObject encode(Plan p) throws JSONException {
        JSONObject j=new JSONObject();
        j.put("id",p.id).put("name",p.name).put("cycles",p.cycles).put("timeLimitMs",p.timeLimitMs)
            .put("startDelayMs",p.startDelayMs).put("cycleGapMs",p.cycleGapMs)
            .put("timingJitterMs",p.timingJitterMs).put("positionJitterPx",p.positionJitterPx)
            .put("fastBatch",p.fastBatch).put("keepScreenOn",p.keepScreenOn).put("volumeStop",p.volumeStop);
        JSONArray steps=new JSONArray();
        for(Plan.Step s:p.steps){
            JSONObject a=new JSONObject().put("kind",s.kind.name()).put("name",s.name).put("enabled",s.enabled)
                .put("x",s.x).put("y",s.y).put("endX",s.endX).put("endY",s.endY)
                .put("durationMs",s.durationMs).put("gapMs",s.gapMs).put("repeats",s.repeats);
            JSONArray points=new JSONArray();for(Plan.Point v:s.points)points.put(new JSONArray().put(v.x).put(v.y));
            a.put("points",points);steps.put(a);
        }
        return j.put("steps",steps);
    }
    public static Plan decode(JSONObject j) throws JSONException {
        Plan p=new Plan();p.id=j.optString("id",p.id);p.name=j.optString("name","読み込んだパターン");
        p.cycles=j.optLong("cycles",0);p.timeLimitMs=j.optLong("timeLimitMs",0);p.startDelayMs=j.optLong("startDelayMs",3000);
        p.cycleGapMs=j.optLong("cycleGapMs",0);p.timingJitterMs=j.optInt("timingJitterMs",0);p.positionJitterPx=j.optInt("positionJitterPx",0);
        p.fastBatch=j.optBoolean("fastBatch",true);p.keepScreenOn=j.optBoolean("keepScreenOn",true);p.volumeStop=j.optBoolean("volumeStop",true);
        JSONArray steps=j.getJSONArray("steps");if(steps.length()>500)throw new IllegalArgumentException("操作は500件までです");
        for(int i=0;i<steps.length();i++){
            JSONObject a=steps.getJSONObject(i);Plan.Step s=Plan.Step.create(Plan.Kind.valueOf(a.getString("kind")));
            s.name=a.optString("name","");s.enabled=a.optBoolean("enabled",true);s.x=a.optDouble("x",.5);s.y=a.optDouble("y",.5);
            s.endX=a.optDouble("endX",.5);s.endY=a.optDouble("endY",.25);s.durationMs=a.optLong("durationMs",s.durationMs);
            s.gapMs=a.optLong("gapMs",s.gapMs);s.repeats=a.optInt("repeats",1);
            JSONArray pts=a.optJSONArray("points");s.points.clear();
            if(pts!=null){if(pts.length()>2000)throw new IllegalArgumentException("軌跡の点数が多すぎます");
                for(int z=0;z<pts.length();z++){JSONArray v=pts.getJSONArray(z);s.points.add(new Plan.Point(v.getDouble(0),v.getDouble(1)));}}
            p.steps.add(s);
        }
        p.validate();return p;
    }
    public static String exportPlan(Plan p) throws JSONException {return new JSONObject().put("format","TapPilot").put("schema",1).put("plan",encode(p)).toString(2);}
    public static Plan importPlan(String data) throws JSONException {
        if(data.length()>1048576)throw new IllegalArgumentException("ファイルは1MB以内です");
        JSONObject j=new JSONObject(data);
        if(!"TapPilot".equals(j.optString("format"))||j.optInt("schema")!=1)throw new IllegalArgumentException("TapPilot形式（schema 1）のJSONファイルを選んでください");
        Plan p=decode(j.getJSONObject("plan"));p.id=java.util.UUID.randomUUID().toString();return p;
    }
}
