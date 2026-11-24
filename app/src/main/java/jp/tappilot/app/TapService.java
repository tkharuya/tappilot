package jp.tappilot.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.Locale;

public final class TapService extends AccessibilityService implements GesturePort.Screen {
    public static TapService instance;
    public final Handler uiHandler=new Handler(Looper.getMainLooper());
    private final Handler runHandler=new Handler(Looper.getMainLooper());
    public Runner runner;
    public OverlayController overlay;
    public PlanStore store;
    private int screenWidth,screenHeight,rotation,consumedKey;
    private DisplayManager.DisplayListener displayListener;
    private BroadcastReceiver screenReceiver;
    private long lastNotification;
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(overlay!=null)overlay.refreshStatus();
        if(overlay!=null&&overlay.visible()&&SystemClock.uptimeMillis()-lastNotification>1000){notification();lastNotification=SystemClock.uptimeMillis();}
        uiHandler.postDelayed(this,250);
    }};
    @Override protected void onServiceConnected(){
        super.onServiceConnected();instance=this;store=new PlanStore(this);readScreen();
        GesturePort port=new GesturePort(this,uiHandler,this);
        runner=new Runner(new Runner.Scheduler(){
            public long now(){return SystemClock.uptimeMillis();}
            public void post(Runnable r,long delay){runHandler.postDelayed(r,Math.max(0,delay));}
            public void clear(){runHandler.removeCallbacksAndMessages(null);}
        },port,()->{if(overlay!=null)overlay.runnerChanged();});
        overlay=new OverlayController(this);
        displayListener=new DisplayManager.DisplayListener(){
            public void onDisplayAdded(int id){}
            public void onDisplayRemoved(int id){}
            public void onDisplayChanged(int id){if(id==android.view.Display.DEFAULT_DISPLAY)checkScreen();}
        };
        getSystemService(DisplayManager.class).registerDisplayListener(displayListener,uiHandler);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("controls","実行操作",NotificationManager.IMPORTANCE_LOW));
        screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(runner!=null)runner.stop("画面オフで停止");if(overlay!=null)overlay.hide();}};
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver,new IntentFilter(Intent.ACTION_SCREEN_OFF));
        uiHandler.post(ticker);
    }
    private void readScreen(){DisplayMetrics m=new DisplayMetrics();Display d=getSystemService(WindowManager.class).getDefaultDisplay();d.getRealMetrics(m);screenWidth=m.widthPixels;screenHeight=m.heightPixels;rotation=d.getRotation();}
    private void checkScreen(){int oldW=screenWidth,oldH=screenHeight,oldRotation=rotation;readScreen();
        if((oldW!=screenWidth||oldH!=screenHeight||oldRotation!=rotation)&&overlay!=null){runner.stop("画面サイズ・向きの変更で停止。位置を確認してください");overlay.screenChanged();}}
    @Override public int width(){return screenWidth;}
    @Override public int height(){return screenHeight;}
    public void showPanel(){if(store.consent())overlay.show();else Ui.toast(this,"TapPilotを開いて画面操作への同意を設定してください");}
    public void play(){
        try{
            KeyguardManager k=getSystemService(KeyguardManager.class);
            if(k.isKeyguardLocked()){Ui.toast(this,"画面ロックを解除してください");return;}
            Plan p=store.current();
            if(overlay!=null&&!overlay.clearOfPanel(p)){Ui.toast(this,"操作位置・軌跡がパネルと重なっています。パネル上部をドラッグして移動してください");return;}
            runner.start(p);
        }catch(Exception e){Ui.toast(this,e.getMessage());}
    }
    public void pauseResume(){if(runner.state()==Runner.State.PAUSED)runner.resume();else if(runner.active())runner.pause();}
    public void stop(){runner.stop("停止しました");}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){if(runner!=null)runner.stop("Androidにより中断");}
    @Override protected boolean onKeyEvent(KeyEvent event){
        int k=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_UP&&consumedKey==k){consumedKey=0;return true;}
        if((k==KeyEvent.KEYCODE_VOLUME_DOWN||k==KeyEvent.KEYCODE_VOLUME_UP)&&event.getAction()==KeyEvent.ACTION_DOWN
            &&runner!=null&&runner.state()!=Runner.State.STOPPED&&runner.snapshot()!=null&&runner.snapshot().volumeStop){
            consumedKey=k;runner.stop("音量キーで停止");return true;
        }
        return super.onKeyEvent(event);
    }
    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);checkScreen();
    }
    public String stats(){return String.format(Locale.JAPAN,"%,dタップ  ·  %,d周  ·  %.1f秒\n送信完了 %.1fタップ/秒",runner.taps(),runner.cycles(),runner.elapsed()/1000.0,runner.tapsPerSecond());}
    public void notification(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getBroadcast(this,2,new Intent(this,ControlReceiver.class).setAction("jp.tappilot.STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"controls").setSmallIcon(R.drawable.ic_notification).setContentTitle("TapPilot · "+runner.message())
            .setContentText(runner.taps()+"タップ / "+runner.cycles()+"周").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(runner.active())
            .addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
        getSystemService(NotificationManager.class).notify(7,n);
    }
    public void clearNotification(){getSystemService(NotificationManager.class).cancel(7);}
    @Override public void onDestroy(){
        if(runner!=null)runner.stop("サービス終了");if(overlay!=null)overlay.hide();
        if(screenReceiver!=null)unregisterReceiver(screenReceiver);
        if(displayListener!=null)getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        runHandler.removeCallbacksAndMessages(null);uiHandler.removeCallbacksAndMessages(null);clearNotification();instance=null;super.onDestroy();
    }
}
