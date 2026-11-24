package jp.tappilot.app;
import android.content.*;
public final class ControlReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){if("jp.tappilot.STOP".equals(intent.getAction())&&TapService.instance!=null)TapService.instance.stop();}
}
