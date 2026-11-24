package jp.tappilot.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public final class OverlayController {
    private final TapService s;
    private final WindowManager wm;
    private LinearLayout panel;
    private TextView title,status,stats;
    private Button play,pause,position;
    private WindowManager.LayoutParams panelParams;
    private final List<View> markers=new ArrayList<>();
    private View routes,capture;
    private boolean marksVisible=true,folded=false;
    private int panelX,panelY;
    private Runner.State displayedState=Runner.State.STOPPED;
    public OverlayController(TapService service){s=service;wm=s.getSystemService(WindowManager.class);panelX=s.store.preferences().getInt("panelX",Ui.dp(s,12));panelY=s.store.preferences().getInt("panelY",Ui.dp(s,110));}
    private WindowManager.LayoutParams params(int width,int height,int flags){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if(Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);
        return p;
    }
    public boolean visible(){return panel!=null;}
    public boolean clearOfPanel(Plan plan){
        if(panel==null)return true;
        RectF r=new RectF(panelParams.x-Ui.dp(s,4),panelParams.y-Ui.dp(s,4),panelParams.x+panel.getWidth()+Ui.dp(s,4),panelParams.y+panel.getHeight()+Ui.dp(s,4));
        for(Plan.Step a:plan.steps){
            if(!a.enabled||!a.usesCoordinates())continue;
            float x=(float)a.x*(s.width()-1),y=(float)a.y*(s.height()-1),ex=(float)a.endX*(s.width()-1),ey=(float)a.endY*(s.height()-1);
            if(a.kind==Plan.Kind.TAP||a.kind==Plan.Kind.DOUBLE_TAP||a.kind==Plan.Kind.HOLD){RectF b=new RectF(r);b.inset(-plan.positionJitterPx,-plan.positionJitterPx);if(b.contains(x,y))return false;}
            if(a.kind==Plan.Kind.SWIPE){if(intersects(r,x,y,ex,ey))return false;}
            if(a.kind==Plan.Kind.PATH)for(int i=1;i<a.points.size();i++){Plan.Point v=a.points.get(i-1),w=a.points.get(i);if(intersects(r,(float)v.x*(s.width()-1),(float)v.y*(s.height()-1),(float)w.x*(s.width()-1),(float)w.y*(s.height()-1)))return false;}
            if(a.kind==Plan.Kind.PINCH_IN||a.kind==Plan.Kind.PINCH_OUT){if(intersects(r,x,y,ex,ey)||intersects(r,x,y,2*x-ex,2*y-ey))return false;}
        }
        return true;
    }
    private boolean intersects(RectF r,float x,float y,float ex,float ey){
        // Segment/rectangle intersection; avoids rejecting a diagonal solely because of its bounds.
        if(r.contains(x,y)||r.contains(ex,ey))return true;
        float dx=ex-x,dy=ey-y,low=0,high=1;float[] p={-dx,dx,-dy,dy},q={x-r.left,r.right-x,y-r.top,r.bottom-y};
        for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<0)return false;}else{float t=q[i]/p[i];if(p[i]<0)low=Math.max(low,t);else high=Math.min(high,t);if(low>high)return false;}}
        return true;
    }
    public void show(){
        if(panel!=null){refreshMarkers();return;}
        panel=Ui.column(s);panel.setPadding(Ui.dp(s,10),Ui.dp(s,8),Ui.dp(s,10),Ui.dp(s,10));panel.setBackground(Ui.shape(Ui.CARD,Ui.dp(s,18)));panel.setElevation(Ui.dp(s,12));
        LinearLayout handle=Ui.row(s);title=Ui.text(s,"⠿  TapPilot",16,Ui.ACCENT);title.setTypeface(null,Typeface.BOLD);
        handle.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button collapse=Ui.button(s,"−",Ui.LINE);collapse.setContentDescription("パネルを折りたたむ");collapse.setTextSize(14);handle.addView(collapse,new LinearLayout.LayoutParams(Ui.dp(s,44),Ui.dp(s,38)));panel.addView(handle);
        status=Ui.text(s,"停止中",12,Ui.TEXT);panel.addView(status);stats=Ui.text(s,"",11,Ui.MUTED);panel.addView(stats);
        LinearLayout controls=Ui.row(s);play=Ui.button(s,"▶",Ui.ACCENT);pause=Ui.button(s,"Ⅱ",Ui.LINE);Button stop=Ui.button(s,"■",Ui.LINE),add=Ui.button(s,"＋",Ui.LINE);
        stop.setTextColor(Ui.RED);play.setContentDescription("開始");pause.setContentDescription("一時停止・再開");stop.setContentDescription("停止");add.setContentDescription("操作を追加");
        for(Button b:new Button[]{play,pause,stop,add})Ui.weighted(controls,b);panel.addView(controls);
        LinearLayout extras=Ui.row(s);position=Ui.button(s,"位置",Ui.LINE);Button steps=Ui.button(s,"手順",Ui.LINE),settings=Ui.button(s,"設定",Ui.LINE),close=Ui.button(s,"×",Ui.LINE);
        for(Button b:new Button[]{position,steps,settings,close}){b.setTextSize(12);b.setPadding(0,0,0,0);Ui.weighted(extras,b);}panel.addView(extras);
        int w=Math.min(Ui.dp(s,290),s.width()-Ui.dp(s,16));panelParams=params(w,-2,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        panelParams.x=Math.max(0,Math.min(panelX,s.width()-w));panelParams.y=Math.max(Ui.dp(s,28),Math.min(panelY,s.height()-Ui.dp(s,220)));
        wm.addView(panel,panelParams);drag(handle);refreshStatus();refreshMarkers();s.notification();
        play.setOnClickListener(v->{if(s.runner.state()==Runner.State.PAUSED)s.runner.resume();else if(!s.runner.active())s.play();});
        pause.setOnClickListener(v->s.pauseResume());stop.setOnClickListener(v->s.stop());add.setOnClickListener(v->chooseKind());
        position.setOnClickListener(v->{if(s.runner.state()!=Runner.State.STOPPED){Ui.toast(s,"停止してから位置を変更してください");return;}marksVisible=!marksVisible;refreshMarkers();});
        steps.setOnClickListener(v->stepList());close.setOnClickListener(v->{s.stop();hide();});
        settings.setOnClickListener(v->{s.stop();hide();s.startActivity(new Intent(s,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));});
        collapse.setOnClickListener(v->{folded=!folded;stats.setVisibility(folded?View.GONE:View.VISIBLE);extras.setVisibility(folded?View.GONE:View.VISIBLE);collapse.setText(folded?"＋":"−");});
    }
    private void drag(View handle){handle.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;
        public boolean onTouch(View v,MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:x=e.getRawX();y=e.getRawY();px=panelParams.x;py=panelParams.y;return true;
                case MotionEvent.ACTION_MOVE:panelParams.x=Math.max(0,Math.min(s.width()-panelParams.width,px+(int)(e.getRawX()-x)));panelParams.y=Math.max(Ui.dp(s,24),Math.min(s.height()-panel.getHeight(),py+(int)(e.getRawY()-y)));wm.updateViewLayout(panel,panelParams);return true;
                case MotionEvent.ACTION_UP:panelX=panelParams.x;panelY=panelParams.y;s.store.preferences().edit().putInt("panelX",panelX).putInt("panelY",panelY).apply();v.performClick();return true;
                default:return true;
            }
        }
    });}
    private void remove(View v){if(v!=null)try{wm.removeView(v);}catch(IllegalArgumentException ignored){}}
    public void hide(){remove(capture);capture=null;clearMarkers();remove(panel);panel=null;s.clearNotification();}
    public void screenChanged(){if(capture!=null){remove(capture);capture=null;}if(panel!=null){remove(panel);panel=null;clearMarkers();show();}}
    public void runnerChanged(){
        if(panel==null)return;
        Runner.State state=s.runner.state();
        if(state!=displayedState){displayedState=state;if(state==Runner.State.STOPPED)refreshMarkers();else clearMarkers();}
        boolean keep=state!=Runner.State.STOPPED&&s.runner.snapshot()!=null&&s.runner.snapshot().keepScreenOn;
        int flags=panelParams.flags;panelParams.flags=keep?flags|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON:flags&~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        wm.updateViewLayout(panel,panelParams);refreshStatus();s.notification();
    }
    public void refreshStatus(){if(panel==null)return;String message=s.runner.message();if(s.runner.state()==Runner.State.COUNTDOWN)message="開始まで "+String.format(java.util.Locale.JAPAN,"%.1f",s.runner.countdown()/1000.0)+"秒";
        status.setText(message);stats.setText(s.stats());play.setText(s.runner.state()==Runner.State.PAUSED?"▶再開":"▶");play.setEnabled(!s.runner.active());pause.setText(s.runner.state()==Runner.State.PAUSED?"▶":"Ⅱ");position.setText(marksVisible?"位置●":"位置○");}
    private void clearMarkers(){for(View v:markers)remove(v);markers.clear();remove(routes);routes=null;}
    public void refreshMarkers(){
        clearMarkers();if(panel==null||!marksVisible||capture!=null||s.runner.state()!=Runner.State.STOPPED)return;
        Plan plan=s.store.current();routes=new RouteView(plan);
        wm.addView(routes,params(-1,-1,WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE));
        for(int i=0;i<plan.steps.size();i++){
            Plan.Step step=plan.steps.get(i);if(!step.enabled||!step.usesCoordinates())continue;
            addMarker(plan,i,false);
            if(step.kind==Plan.Kind.SWIPE||step.kind==Plan.Kind.PINCH_IN||step.kind==Plan.Kind.PINCH_OUT)addMarker(plan,i,true);
        }
    }
    private void addMarker(Plan plan,int index,boolean end){
        Plan.Step step=plan.steps.get(index);TextView mark=Ui.text(s,(index+1)+(end?"B":""),13,Ui.BG);mark.setGravity(Gravity.CENTER);mark.setTypeface(null,Typeface.BOLD);
        mark.setBackground(Ui.shape(end?Ui.PURPLE:Ui.ACCENT,Ui.dp(s,24)));mark.setContentDescription("操作"+(index+1)+(end?"の終点":"の位置"));
        int size=Ui.dp(s,38);WindowManager.LayoutParams p=params(size,size,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        p.x=(int)((end?step.endX:step.x)*(s.width()-1))-size/2;p.y=(int)((end?step.endY:step.y)*(s.height()-1))-size/2;
        wm.addView(mark,p);markers.add(mark);
        mark.setOnTouchListener(new View.OnTouchListener(){float downX,downY;long downAt;boolean moved;
            public boolean onTouch(View v,MotionEvent event){
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:downX=event.getRawX();downY=event.getRawY();downAt=android.os.SystemClock.uptimeMillis();moved=false;return true;
                    case MotionEvent.ACTION_MOVE:
                        if(Math.hypot(event.getRawX()-downX,event.getRawY()-downY)>Ui.dp(s,5))moved=true;
                        if(moved){double x=Math.max(0,Math.min(1,event.getRawX()/(s.width()-1))),y=Math.max(0,Math.min(1,event.getRawY()/(s.height()-1)));
                            if(end){step.endX=x;step.endY=y;}else{double dx=x-step.x,dy=y-step.y;step.x=x;step.y=y;if(step.kind==Plan.Kind.PATH)for(Plan.Point pt:step.points){pt.x=Math.max(0,Math.min(1,pt.x+dx));pt.y=Math.max(0,Math.min(1,pt.y+dy));}}
                            p.x=(int)(x*(s.width()-1))-size/2;p.y=(int)(y*(s.height()-1))-size/2;wm.updateViewLayout(mark,p);if(routes!=null)routes.invalidate();}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(moved){try{s.store.save(plan);}catch(Exception e){Ui.toast(s,e.getMessage());}refreshMarkers();}
                        else edit(index);v.performClick();return true;
                    case MotionEvent.ACTION_CANCEL:refreshMarkers();return true;
                    default:return true;
                }
            }
        });
    }
    public void chooseKind(){
        if(s.runner.state()!=Runner.State.STOPPED){Ui.toast(s,"停止してから操作を追加してください");return;}
        AlertDialog d=new AlertDialog.Builder(s,R.style.AppTheme).setTitle("操作を追加").setItems(Plan.KIND_NAMES,(a,i)->add(Plan.Kind.values()[i])).create();d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.show();Ui.decorate(d);
    }
    private void add(Plan.Kind kind){
        Plan plan=s.store.current();if(plan.steps.size()>=500){Ui.toast(s,"操作は500件までです");return;}
        Plan.Step step=Plan.Step.create(kind);if(kind==Plan.Kind.PATH){step.points.add(new Plan.Point(step.x,step.y));step.points.add(new Plan.Point(step.endX,step.endY));}
        plan.steps.add(step);s.store.save(plan);
        if(step.usesCoordinates())captureAt(plan.steps.size()-1);else edit(plan.steps.size()-1);
    }
    public void edit(int index){
        if(s.runner.state()!=Runner.State.STOPPED){Ui.toast(s,"停止してから編集してください");return;}
        Plan p=s.store.current();if(index<0||index>=p.steps.size())return;
        Ui.stepEditor(s,p.steps.get(index),(updated,pick)->{p.steps.set(index,updated);s.store.save(p);refreshMarkers();if(pick)captureAt(index);});
    }
    private void stepList(){
        if(s.runner.state()!=Runner.State.STOPPED){Ui.toast(s,"停止してから編集してください");return;}
        Plan p=s.store.current();if(p.steps.isEmpty()){chooseKind();return;}
        String[] names=new String[p.steps.size()];for(int i=0;i<names.length;i++)names[i]=(i+1)+". "+p.steps.get(i).title();
        AlertDialog d=new AlertDialog.Builder(s,R.style.AppTheme).setTitle("操作を選択").setItems(names,(a,i)->stepMenu(i)).create();d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.show();Ui.decorate(d);
    }
    private void stepMenu(int index){
        String[] options={"編集","画面で位置・軌跡を指定","複製","上へ移動","下へ移動","削除"};
        AlertDialog d=new AlertDialog.Builder(s,R.style.AppTheme).setTitle("操作 "+(index+1)).setItems(options,(a,which)->{
            Plan p=s.store.current();if(index>=p.steps.size())return;
            if(which==0){edit(index);return;}if(which==1){if(p.steps.get(index).usesCoordinates())captureAt(index);return;}
            if(which==2&&p.steps.size()<500)p.steps.add(index+1,p.steps.get(index).copy());
            if(which==3&&index>0)java.util.Collections.swap(p.steps,index,index-1);
            if(which==4&&index+1<p.steps.size())java.util.Collections.swap(p.steps,index,index+1);
            if(which==5)p.steps.remove(index);s.store.save(p);refreshMarkers();
        }).create();d.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);d.show();Ui.decorate(d);
    }
    public void captureAt(int index){
        if(s.runner.state()!=Runner.State.STOPPED)s.stop();show();remove(capture);capture=null;clearMarkers();
        Plan plan=s.store.current();if(index<0||index>=plan.steps.size())return;
        capture=new CaptureView(plan,index);wm.addView(capture,params(-1,-1,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE));
        panel.setVisibility(View.GONE);
    }
    private void finishCapture(){remove(capture);capture=null;if(panel!=null)panel.setVisibility(View.VISIBLE);refreshMarkers();}
    private final class CaptureView extends View {
        final Plan plan;final int index;final Paint paint=new Paint(3);final List<Plan.Point> drawn=new ArrayList<>();boolean drawing;
        CaptureView(Plan p,int i){super(s);plan=p;index=i;setBackgroundColor(0x2010151F);setContentDescription("操作位置の指定画面");}
        private boolean line(){Plan.Kind k=plan.steps.get(index).kind;return k==Plan.Kind.SWIPE||k==Plan.Kind.PATH||k==Plan.Kind.PINCH_IN||k==Plan.Kind.PINCH_OUT;}
        @Override protected void onDraw(Canvas c){
            paint.setColor(Ui.CARD);c.drawRoundRect(Ui.dp(s,12),Ui.dp(s,40),getWidth()-Ui.dp(s,12),Ui.dp(s,136),Ui.dp(s,16),Ui.dp(s,16),paint);
            paint.setColor(Ui.ACCENT);paint.setTextSize(Ui.dp(s,16));c.drawText(line()?"始点から終点へ指でなぞる":"操作する場所を1回タップ",Ui.dp(s,25),Ui.dp(s,74),paint);
            paint.setColor(Ui.MUTED);paint.setTextSize(Ui.dp(s,12));c.drawText("位置指定中は下のアプリを操作しません",Ui.dp(s,25),Ui.dp(s,100),paint);
            paint.setColor(Ui.RED);paint.setTextSize(Ui.dp(s,14));c.drawText("キャンセル",getWidth()-Ui.dp(s,105),Ui.dp(s,124),paint);
            paint.setStrokeWidth(Ui.dp(s,4));paint.setColor(Ui.ACCENT);
            for(int i=1;i<drawn.size();i++)c.drawLine((float)drawn.get(i-1).x*(getWidth()-1),(float)drawn.get(i-1).y*(getHeight()-1),(float)drawn.get(i).x*(getWidth()-1),(float)drawn.get(i).y*(getHeight()-1),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(e.getY()<Ui.dp(s,140)&&e.getY()>Ui.dp(s,35)){finishCapture();return true;}drawn.clear();drawing=true;}
            if(!drawing)return true;
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){drawing=false;finishCapture();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){
                Plan.Point p=new Plan.Point(Math.max(0,Math.min(1,e.getRawX()/(s.width()-1))),Math.max(0,Math.min(1,e.getRawY()/(s.height()-1))));
                if(drawn.size()<1999||e.getActionMasked()==MotionEvent.ACTION_UP)drawn.add(p);invalidate();
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                drawing=false;Plan.Step step=plan.steps.get(index);Plan.Point a=drawn.get(0),b=drawn.get(drawn.size()-1);
                step.x=a.x;step.y=a.y;
                if(line()){step.endX=b.x;step.endY=b.y;}
                if(step.kind==Plan.Kind.PATH){step.points.clear();for(Plan.Point p:drawn)step.points.add(p.copy());}
                try{step.validate();s.store.save(plan);finishCapture();Ui.toast(s,"位置を保存しました。マーカーはドラッグで移動、タップで設定できます");}
                catch(Exception x){Ui.toast(s,x.getMessage());drawn.clear();invalidate();}
                performClick();
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private final class RouteView extends View {
        final Plan plan;final Paint p=new Paint(3);RouteView(Plan plan){super(s);this.plan=plan;}
        @Override protected void onDraw(Canvas c){p.setColor(0xBBA695F7);p.setStrokeWidth(Ui.dp(s,2));
            for(Plan.Step a:plan.steps){if(!a.enabled)continue;
                if(a.kind==Plan.Kind.SWIPE||a.kind==Plan.Kind.PINCH_IN||a.kind==Plan.Kind.PINCH_OUT)c.drawLine((float)a.x*(s.width()-1),(float)a.y*(s.height()-1),(float)a.endX*(s.width()-1),(float)a.endY*(s.height()-1),p);
                if(a.kind==Plan.Kind.PATH)for(int i=1;i<a.points.size();i++)c.drawLine((float)a.points.get(i-1).x*(s.width()-1),(float)a.points.get(i-1).y*(s.height()-1),(float)a.points.get(i).x*(s.width()-1),(float)a.points.get(i).y*(s.height()-1),p);
            }
        }
    }
}
