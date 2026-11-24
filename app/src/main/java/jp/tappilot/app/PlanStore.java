package jp.tappilot.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public final class PlanStore {
    private final SharedPreferences prefs;
    public PlanStore(Context c){prefs=c.getSharedPreferences("tappilot",Context.MODE_PRIVATE);}
    public List<Plan> all(){
        List<Plan> out=new ArrayList<>();String raw=prefs.getString("profiles","[]");
        try {JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++)out.add(PlanCodec.decode(a.getJSONObject(i)));}
        catch(Exception e){prefs.edit().putString("recovery",raw).apply();}
        if(out.isEmpty()){Plan p=new Plan();out.add(p);write(out);}
        return out;
    }
    public Plan current(){List<Plan> a=all();String id=prefs.getString("selected","");for(Plan p:a)if(p.id.equals(id))return p;return a.get(0);}
    public void select(String id){prefs.edit().putString("selected",id).apply();}
    public void save(Plan p){p.validate();List<Plan>a=all();boolean found=false;
        for(int i=0;i<a.size();i++)if(a.get(i).id.equals(p.id)){a.set(i,p.copy());found=true;break;}
        if(!found)a.add(p.copy());write(a);select(p.id);
    }
    public void remove(String id){List<Plan>a=all();a.removeIf(p->p.id.equals(id));if(a.isEmpty())a.add(new Plan());write(a);select(a.get(0).id);}
    private void write(List<Plan> a){try{JSONArray j=new JSONArray();for(Plan p:a)j.put(PlanCodec.encode(p));prefs.edit().putString("profiles",j.toString()).apply();}catch(Exception e){throw new IllegalStateException("パターンを保存できません",e);}}
    public boolean consent(){return prefs.getBoolean("consent",false);}
    public void consent(boolean yes){prefs.edit().putBoolean("consent",yes).apply();}
    public SharedPreferences preferences(){return prefs;}
}
