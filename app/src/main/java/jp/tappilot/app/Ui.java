package jp.tappilot.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.Locale;

public final class Ui {
    public static final int BG=Color.rgb(16,21,31),CARD=Color.rgb(26,34,48),TEXT=Color.rgb(240,245,251),MUTED=Color.rgb(158,174,193),ACCENT=Color.rgb(83,224,197),PURPLE=Color.rgb(166,149,247),RED=Color.rgb(255,126,144),LINE=Color.rgb(48,63,80);
    public interface SaveStep {void save(Plan.Step step,boolean capture);}
    private Ui(){}
    public static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    public static GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    public static TextView text(Context c,String s,int size,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(c,3),1);return v;}
    public static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(android.view.Gravity.CENTER_VERTICAL);return l;}
    public static LinearLayout card(Context c){LinearLayout l=column(c);l.setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,16));l.setBackground(shape(CARD,dp(c,20)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(c,12);l.setLayoutParams(p);return l;}
    public static void gap(LinearLayout l,int h){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),h)));}
    public static Button button(Context c,String s,int color){Button b=new Button(c);b.setText(s);b.setTextSize(14);b.setTextColor(color==ACCENT?BG:TEXT);b.setAllCaps(false);b.setMinHeight(dp(c,44));b.setMinimumHeight(dp(c,44));b.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));b.setBackground(shape(color==ACCENT?ACCENT:LINE,dp(c,12)));return b;}
    public static void weighted(LinearLayout row,View child){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(row.getContext(),3),dp(row.getContext(),4),dp(row.getContext(),3),dp(row.getContext(),4));row.addView(child,p);}
    public static EditText field(Context c,LinearLayout parent,String label,String value,boolean decimal){parent.addView(text(c,label,12,MUTED));EditText e=new EditText(c);e.setSingleLine(true);e.setTextColor(TEXT);e.setTextSize(16);e.setPadding(0,dp(c,7),0,dp(c,7));e.setInputType(decimal?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_NUMBER);e.setText(value);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    public static EditText stringField(Context c,LinearLayout p,String label,String v){EditText e=field(c,p,label,v,false);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);e.setText(v);return e;}
    public static Switch toggle(Context c,LinearLayout p,String label,boolean checked){Switch v=new Switch(c);v.setText(label);v.setTextColor(TEXT);v.setTextSize(14);v.setChecked(checked);v.setPadding(0,dp(c,8),0,dp(c,8));p.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    public static AlertDialog dialog(Context c,String title,View body){
        AlertDialog d=new AlertDialog.Builder(c,R.style.AppTheme).setTitle(title).setView(body).create();
        if(!(c instanceof Activity))d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        return d;
    }
    public static void decorate(AlertDialog d){if(d.getWindow()!=null)d.getWindow().setBackgroundDrawable(shape(CARD,dp(d.getContext(),20)));}
    public static void toast(Context c,String s){Toast.makeText(c,s,Toast.LENGTH_LONG).show();}
    public static long number(EditText e,String label){try{return Long.parseLong(e.getText().toString().trim());}catch(Exception x){throw new IllegalArgumentException(label+"を整数で入力してください");}}
    public static double percent(EditText e){try{double d=Double.parseDouble(e.getText().toString().trim().replace(',','.'))/100;Plan.coordinate(d);return d;}catch(Exception x){throw new IllegalArgumentException("座標は0〜100%で入力してください");}}
    public static String pct(double d){return String.format(Locale.US,"%.2f",d*100);}
    public static String detail(Plan.Step s){
        String pos=s.usesCoordinates()?"  ·  "+pct(s.x)+"%, "+pct(s.y)+"%":"";
        if(s.kind==Plan.Kind.TAP)return s.gapMs+"ms間隔  ·  "+s.repeats+"回"+pos;
        if(s.kind==Plan.Kind.BACK||s.kind==Plan.Kind.HOME||s.kind==Plan.Kind.RECENTS)return s.repeats+"回  ·  後待機 "+s.gapMs+"ms";
        return s.durationMs+"ms  ·  "+s.repeats+"回"+pos;
    }
    public static void stepEditor(Context c,Plan.Step original,SaveStep onSave){
        Plan.Step s=original.copy();
        LinearLayout body=column(c);body.setPadding(dp(c,20),dp(c,8),dp(c,20),dp(c,14));
        EditText name=stringField(c,body,"操作名（省略可）",s.name);
        body.addView(text(c,Plan.KIND_NAMES[s.kind.ordinal()],16,ACCENT));gap(body,10);
        Switch enabled=toggle(c,body,"この操作を有効にする",s.enabled);
        boolean global=s.kind==Plan.Kind.BACK||s.kind==Plan.Kind.HOME||s.kind==Plan.Kind.RECENTS;
        String durationLabel=s.kind==Plan.Kind.TAP?"押下時間（ms）":s.kind==Plan.Kind.DOUBLE_TAP?"2回のタップ間隔（ms）":"動作 / 待機時間（ms）";
        EditText duration=global?null:field(c,body,durationLabel,""+s.durationMs,false);
        EditText gap=field(c,body,s.kind==Plan.Kind.TAP?"タップ間隔・開始→開始（ms）":"操作後の待機（ms）",""+s.gapMs,false);
        if(s.kind==Plan.Kind.TAP){
            LinearLayout presets=row(c);for(long v:new long[]{2,10,50,100}){Button b=button(c,v+"ms",LINE);b.setTextSize(11);weighted(presets,b);b.setOnClickListener(w->{gap.setText(""+v);duration.setText("1");});}body.addView(presets);
            body.addView(text(c,"2msは指定値です。実際の速度はAndroid・画面更新・対象アプリで変わります。取りこぼす場合は20〜100msへ。",12,MUTED));
        }
        EditText repeats=field(c,body,"この操作の繰り返し回数",""+s.repeats,false);
        EditText x=null,y=null,ex=null,ey=null;
        if(s.usesCoordinates()){
            gap(body,8);body.addView(text(c,"画面全体に対する位置（%）",13,ACCENT));
            x=field(c,body,"X / 横（0〜100%）",pct(s.x),true);y=field(c,body,"Y / 縦（0〜100%）",pct(s.y),true);
            if(s.kind==Plan.Kind.SWIPE||s.kind==Plan.Kind.PINCH_IN||s.kind==Plan.Kind.PINCH_OUT){
                ex=field(c,body,"終点X / ピンチの外側X（%）",pct(s.endX),true);ey=field(c,body,"終点Y / ピンチの外側Y（%）",pct(s.endY),true);
            }
        }
        TextView error=text(c,"",13,RED);body.addView(error);
        LinearLayout actions=row(c);Button cancel=button(c,"閉じる",LINE),save=button(c,"保存",ACCENT);weighted(actions,cancel);weighted(actions,save);body.addView(actions);
        Button capture=s.usesCoordinates()?button(c,"保存して画面で位置・軌跡を指定",LINE):null;
        if(capture!=null){gap(body,8);body.addView(capture);}
        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.addView(body);
        AlertDialog d=dialog(c,"操作の編集",scroll);d.show();decorate(d);
        final EditText fx=x,fy=y,fex=ex,fey=ey;
        java.util.function.Consumer<Boolean> commit=pick->{
            try{
                s.name=name.getText().toString().trim();s.enabled=enabled.isChecked();
                if(duration!=null)s.durationMs=number(duration,"動作時間");
                s.gapMs=number(gap,"間隔");long r=number(repeats,"繰り返し回数");Plan.range(r,1,1000000,"繰り返し回数");s.repeats=(int)r;
                if(fx!=null){double nx=percent(fx),ny=percent(fy);if(s.kind==Plan.Kind.PATH&&!s.points.isEmpty())for(Plan.Point pt:s.points){pt.x+=nx-s.x;pt.y+=ny-s.y;}s.x=nx;s.y=ny;}if(fex!=null){s.endX=percent(fex);s.endY=percent(fey);}
                if(s.kind==Plan.Kind.PATH && s.points.isEmpty()){s.points.add(new Plan.Point(s.x,s.y));s.points.add(new Plan.Point(s.endX,s.endY));}
                s.validate();onSave.save(s,pick);d.dismiss();
            }catch(Exception e){error.setText(e.getMessage());}
        };
        cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->commit.accept(false));if(capture!=null)capture.setOnClickListener(v->commit.accept(true));
    }
}
