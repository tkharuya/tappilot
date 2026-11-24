package jp.tappilot.app;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public final class PlaygroundActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout header=Ui.column(this);header.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,10));header.addView(Ui.text(this,"動作テスト",24,Ui.TEXT));header.addView(Ui.text(this,"下のエリアでタップ・長押し・スライド・ピンチを確認",12,Ui.MUTED));
        LinearLayout actions=Ui.row(this);Button back=Ui.button(this,"戻る",Ui.LINE),panel=Ui.button(this,"パネルを表示",Ui.ACCENT),reset=Ui.button(this,"リセット",Ui.LINE);for(Button a:new Button[]{back,panel,reset}){a.setTextSize(12);Ui.weighted(actions,a);}header.addView(actions);root.addView(header);
        TouchArea area=new TouchArea();root.addView(area,new LinearLayout.LayoutParams(-1,0,1));back.setOnClickListener(v->finish());panel.setOnClickListener(v->{if(TapService.instance!=null)TapService.instance.showPanel();else Ui.toast(this,"先に画面操作を有効にしてください");});reset.setOnClickListener(v->{area.taps=area.holds=area.swipes=0;area.zoom=1;area.trace.reset();area.invalidate();});setContentView(root);root.requestApplyInsets();}
    private final class TouchArea extends View {
        final Paint p=new Paint(3);final Path trace=new Path();int taps,holds,swipes;float zoom=1,downX,downY;long downAt;boolean multi;
        final ScaleGestureDetector scale;
        TouchArea(){super(PlaygroundActivity.this);setBackgroundColor(Ui.CARD);scale=new ScaleGestureDetector(PlaygroundActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.1f,Math.min(10,zoom*d.getScaleFactor()));invalidate();return true;}});setContentDescription("自動タップの動作テスト領域");}
        @Override public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);
            if(e.getPointerCount()>1)multi=true;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downAt=android.os.SystemClock.uptimeMillis();multi=false;trace.reset();trace.moveTo(downX,downY);}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE)trace.lineTo(e.getX(),e.getY());
            if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!multi){if(Math.hypot(e.getX()-downX,e.getY()-downY)>Ui.dp(getContext(),22))swipes++;else if(android.os.SystemClock.uptimeMillis()-downAt>=500)holds++;else taps++;}performClick();}invalidate();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDraw(Canvas c){int cx=getWidth()/2,cy=getHeight()/2;
            p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(Ui.LINE);for(int x=0;x<getWidth();x+=Ui.dp(getContext(),40))c.drawLine(x,0,x,getHeight(),p);for(int y=0;y<getHeight();y+=Ui.dp(getContext(),40))c.drawLine(0,y,getWidth(),y,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),3));p.setColor(Ui.PURPLE);c.drawCircle(cx,cy,Ui.dp(getContext(),65)*zoom,p);p.setColor(Ui.ACCENT);c.drawPath(trace,p);p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.TEXT);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Ui.dp(getContext(),36));c.drawText(String.valueOf(taps),cx,cy-Ui.dp(getContext(),4),p);p.setTextSize(Ui.dp(getContext(),13));c.drawText("TAPS",cx,cy+Ui.dp(getContext(),20),p);
            p.setColor(Ui.MUTED);p.setTextSize(Ui.dp(getContext(),14));c.drawText("長押し "+holds+"   スライド "+swipes+"   拡大 "+String.format(Locale.JAPAN,"%.2f",zoom)+"倍",cx,getHeight()-Ui.dp(getContext(),36),p);p.setTextAlign(Paint.Align.LEFT);
        }
    }
}
