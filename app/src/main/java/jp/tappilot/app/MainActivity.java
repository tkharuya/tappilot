package jp.tappilot.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends Activity {
    private PlanStore store;
    private Plan plan;
    private LinearLayout body;
    private TextView serviceStatus,runStatus;
    private Button permissionButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String exportData;
    private static final int EXPORT=31,IMPORT=32;
    private final Runnable refresh=new Runnable(){public void run(){updateStatus();handler.postDelayed(this,750);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new PlanStore(this);render();}
    @Override public void onResume(){super.onResume();plan=store.current();render();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);render();}
    private void render(){
        plan=store.current();LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,in)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);}
            else v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=Ui.column(this);body.setPadding(Ui.dp(this,20),Ui.dp(this,24),Ui.dp(this,20),Ui.dp(this,30));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();
        body.addView(Ui.text(this,"AUTOMATE YOUR TOUCH",11,Ui.ACCENT));
        TextView logo=Ui.text(this,"TapPilot",36,Ui.TEXT);logo.setTypeface(null,android.graphics.Typeface.BOLD);body.addView(logo);
        body.addView(Ui.text(this,"連打・スライド・手順の繰り返し",15,Ui.MUTED));Ui.gap(body,20);
        LinearLayout ready=Ui.card(this);serviceStatus=Ui.text(this,"",15,Ui.ACCENT);ready.addView(serviceStatus);
        ready.addView(Ui.text(this,"Root不要  ·  通信なし  ·  広告なし",12,Ui.MUTED));Ui.gap(ready,10);
        permissionButton=Ui.button(this,"画面操作を有効化",Ui.LINE);ready.addView(permissionButton);permissionButton.setOnClickListener(v->permission());body.addView(ready);
        LinearLayout controls=Ui.card(this);controls.addView(Ui.text(this,"現在のパターン",12,Ui.MUTED));
        List<Plan> profiles=store.all();Spinner selector=new Spinner(this);List<String> names=new ArrayList<>();int selected=0;
        for(int i=0;i<profiles.size();i++){names.add(profiles.get(i).name);if(profiles.get(i).id.equals(plan.id))selected=i;}
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);selector.setAdapter(adapter);selector.setSelection(selected);controls.addView(selector,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int at,long id){if(!profiles.get(at).id.equals(plan.id)){if(TapService.instance!=null)TapService.instance.stop();store.select(profiles.get(at).id);render();}}});
        LinearLayout profileActions=Ui.row(this);Button newPlan=Ui.button(this,"新規",Ui.LINE),profileMenu=Ui.button(this,"管理",Ui.LINE),templates=Ui.button(this,"プリセット",Ui.LINE);Ui.weighted(profileActions,newPlan);Ui.weighted(profileActions,profileMenu);Ui.weighted(profileActions,templates);controls.addView(profileActions);
        newPlan.setOnClickListener(v->nameDialog(null));profileMenu.setOnClickListener(v->profileMenu());templates.setOnClickListener(v->templates());Ui.gap(controls,10);
        Button floating=Ui.button(this,"フローティング操作を開く",Ui.ACCENT);controls.addView(floating,new LinearLayout.LayoutParams(-1,Ui.dp(this,54)));floating.setOnClickListener(v->openPanel());Ui.gap(controls,8);
        controls.addView(Ui.text(this,"対象アプリを開き、パネルの＋で位置を追加 → ▶で開始。番号をドラッグして位置を調整できます。",12,Ui.MUTED));
        runStatus=Ui.text(this,"",12,Ui.MUTED);controls.addView(runStatus);body.addView(controls);
        LinearLayout section=Ui.row(this);TextView stepTitle=Ui.text(this,"操作の順番",19,Ui.TEXT);stepTitle.setTypeface(null,android.graphics.Typeface.BOLD);section.addView(stepTitle,new LinearLayout.LayoutParams(0,-2,1));section.addView(Ui.text(this,plan.steps.size()+" STEPS",11,Ui.PURPLE));body.addView(section);Ui.gap(body,10);
        LinearLayout addRow=Ui.row(this);Button tap=Ui.button(this,"＋タップ",Ui.LINE),swipe=Ui.button(this,"＋スライド",Ui.LINE),more=Ui.button(this,"＋その他",Ui.LINE);for(Button b:new Button[]{tap,swipe,more}){b.setTextSize(12);Ui.weighted(addRow,b);}body.addView(addRow);Ui.gap(body,10);
        tap.setOnClickListener(v->add(Plan.Kind.TAP));swipe.setOnClickListener(v->add(Plan.Kind.SWIPE));more.setOnClickListener(v->new AlertDialog.Builder(this,R.style.AppTheme).setTitle("操作の種類").setItems(Plan.KIND_NAMES,(d,i)->add(Plan.Kind.values()[i])).show());
        if(plan.steps.isEmpty()){
            LinearLayout empty=Ui.card(this);empty.addView(Ui.text(this,"最初の操作を追加",17,Ui.TEXT));Ui.gap(empty,5);empty.addView(Ui.text(this,"連打はタップ1件で始められます。複数の位置とスライドを追加すると、上から順に実行します。",13,Ui.MUTED));body.addView(empty);
        }
        for(int i=0;i<plan.steps.size();i++)stepCard(i);
        LinearLayout options=Ui.card(this);options.addView(Ui.text(this,"実行設定",17,Ui.TEXT));Ui.gap(options,6);options.addView(Ui.text(this,(plan.cycles==0?"停止まで繰り返す":plan.cycles+"周で停止")+"  ·  開始待ち "+plan.startDelayMs/1000.0+"秒\n"+(plan.timeLimitMs==0?"時間制限なし":plan.timeLimitMs/1000+"秒で停止")+"  ·  音量キー停止 "+(plan.volumeStop?"ON":"OFF"),13,Ui.MUTED));Ui.gap(options,10);Button settings=Ui.button(this,"回数・速度・停止条件を設定",Ui.LINE);options.addView(settings);settings.setOnClickListener(v->runSettings());body.addView(options);
        LinearLayout files=Ui.row(this);Button export=Ui.button(this,"JSON書き出し",Ui.LINE),imp=Ui.button(this,"JSON読み込み",Ui.LINE);Ui.weighted(files,export);Ui.weighted(files,imp);body.addView(files);export.setOnClickListener(v->exportPlan());imp.setOnClickListener(v->{Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(in,IMPORT);});Ui.gap(body,10);
        LinearLayout extras=Ui.row(this);Button test=Ui.button(this,"動作テスト",Ui.LINE),help=Ui.button(this,"使い方",Ui.LINE);Ui.weighted(extras,test);Ui.weighted(extras,help);body.addView(extras);test.setOnClickListener(v->{startActivity(new Intent(this,PlaygroundActivity.class));});help.setOnClickListener(v->help());Ui.gap(body,16);
        body.addView(Ui.text(this,"最小2msは指定間隔です。実測の送信速度をパネルに表示します。画面の回転・折りたたみ・画面オフで停止します。",12,Ui.MUTED));updateStatus();
    }
    private void stepCard(int index){
        Plan.Step step=plan.steps.get(index);LinearLayout card=Ui.card(this);card.setAlpha(step.enabled?1:.55f);LinearLayout top=Ui.row(this);
        TextView badge=Ui.text(this,String.format(Locale.JAPAN,"%02d",index+1),19,Ui.ACCENT);badge.setTypeface(null,android.graphics.Typeface.BOLD);top.addView(badge,new LinearLayout.LayoutParams(Ui.dp(this,42),-2));
        LinearLayout title=Ui.column(this);title.addView(Ui.text(this,step.title(),16,Ui.TEXT));title.addView(Ui.text(this,Ui.detail(step),11,Ui.MUTED));top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=Ui.button(this,"⋮",Ui.LINE);menu.setContentDescription("操作"+(index+1)+"のメニュー");top.addView(menu,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));card.addView(top);card.setOnClickListener(v->edit(index));menu.setOnClickListener(v->stepMenu(index));body.addView(card);
    }
    private void updateStatus(){if(serviceStatus==null)return;boolean connected=TapService.instance!=null;
        if(connected){serviceStatus.setText("●  画面操作の準備完了");serviceStatus.setTextColor(Ui.ACCENT);permissionButton.setText("権限の設定を開く");}
        else {boolean enabled=enabledInSettings();serviceStatus.setText(enabled?"●  サービスの接続待ち":"○  初回設定が必要です");serviceStatus.setTextColor(enabled?Ui.PURPLE:Ui.MUTED);permissionButton.setText(enabled?"接続しない場合はOFF→ON":"画面操作を有効化");}
        if(runStatus!=null)runStatus.setText(connected?TapService.instance.runner.message()+"  ·  "+TapService.instance.runner.taps()+"タップ":"");
    }
    private boolean enabledInSettings(){String value=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);ComponentName component=new ComponentName(this,TapService.class);if(value!=null)for(String item:value.split(":"))if(component.equals(ComponentName.unflattenFromString(item)))return true;return false;}
    private void permission(){
        if(store.consent()){startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
        new AlertDialog.Builder(this,R.style.AppTheme).setTitle("画面操作を許可する")
            .setMessage("TapPilotはユーザー補助機能を使い、あなたが指定した座標へのタップ・スライド・戻る等の操作と、他アプリ上の操作パネルを実現します。\n\n画面内の文字・入力内容は取得しません。通信しません。開始ボタンを押したときだけ手順を実行します。\n\n同意後、Androidの「ダウンロードしたアプリ」などからTapPilotを有効にしてください。")
            .setNegativeButton("閉じる",null).setPositiveButton("同意して設定を開く",(d,w)->{store.consent(true);startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}).show();
    }
    private void openPanel(){
        if(!store.consent()||TapService.instance==null){permission();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!store.preferences().getBoolean("notificationAsked",false)){
            store.preferences().edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);return;
        }
        openPanelNow();
    }
    private void openPanelNow(){if(TapService.instance!=null){TapService.instance.showPanel();moveTaskToBack(true);}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==8)openPanelNow();}
    private void add(Plan.Kind kind){if(plan.steps.size()>=500){Ui.toast(this,"操作は500件までです");return;}Plan.Step step=Plan.Step.create(kind);Ui.stepEditor(this,step,(s,pick)->{plan.steps.add(s);store.save(plan);int index=plan.steps.size()-1;render();if(pick)capture(index);});}
    private void edit(int index){Ui.stepEditor(this,plan.steps.get(index),(s,pick)->{plan.steps.set(index,s);store.save(plan);render();if(pick)capture(index);});}
    private void capture(int index){if(TapService.instance==null||!store.consent()){Ui.toast(this,"操作を保存しました。画面操作を有効にしてから位置を指定してください");return;}moveTaskToBack(true);handler.postDelayed(()->{if(TapService.instance!=null)TapService.instance.overlay.captureAt(index);},450);}
    private void stepMenu(int index){String[] items={"編集","画面で位置・軌跡を指定","有効 / 無効","複製","上へ移動","下へ移動","削除"};new AlertDialog.Builder(this,R.style.AppTheme).setTitle("操作 "+(index+1)).setItems(items,(d,which)->{
        if(which==0){edit(index);return;}if(which==1){if(plan.steps.get(index).usesCoordinates())capture(index);return;}
        if(which==2)plan.steps.get(index).enabled=!plan.steps.get(index).enabled;
        if(which==3&&plan.steps.size()<500)plan.steps.add(index+1,plan.steps.get(index).copy());
        if(which==4&&index>0)Collections.swap(plan.steps,index,index-1);if(which==5&&index+1<plan.steps.size())Collections.swap(plan.steps,index,index+1);if(which==6)plan.steps.remove(index);store.save(plan);render();
    }).show();}
    private void profileMenu(){String[] items={"名前を変更","複製","削除"};new AlertDialog.Builder(this,R.style.AppTheme).setTitle(plan.name).setItems(items,(d,i)->{
        if(i==0)nameDialog(plan);if(i==1){if(store.all().size()>=50){Ui.toast(this,"パターンは50件までです");return;}Plan p=plan.copy();p.id=UUID.randomUUID().toString();p.name=p.name.length()>74?p.name.substring(0,74)+" コピー":p.name+" コピー";store.save(p);render();}
        if(i==2)new AlertDialog.Builder(this,R.style.AppTheme).setTitle("このパターンを削除しますか").setMessage(plan.name).setNegativeButton("閉じる",null).setPositiveButton("削除",(a,b)->{store.remove(plan.id);render();}).show();
    }).show();}
    private void nameDialog(Plan existing){
        if(existing==null&&store.all().size()>=50){Ui.toast(this,"パターンは50件までです");return;}
        LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,22),0,Ui.dp(this,22),Ui.dp(this,10));EditText name=Ui.stringField(this,box,"パターン名",existing==null?"新しいパターン":existing.name);
        AlertDialog d=new AlertDialog.Builder(this,R.style.AppTheme).setTitle(existing==null?"新規パターン":"名前を変更").setView(box).setNegativeButton("閉じる",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{Plan p=existing==null?new Plan():existing.copy();p.name=name.getText().toString().trim();store.save(p);d.dismiss();render();}catch(Exception e){name.setError(e.getMessage());}}));d.show();
    }
    private void runSettings(){
        LinearLayout box=Ui.column(this);box.setPadding(Ui.dp(this,22),Ui.dp(this,10),Ui.dp(this,22),Ui.dp(this,18));
        EditText cycles=Ui.field(this,box,"周回数（0 = 停止するまで）",""+plan.cycles,false);
        EditText seconds=Ui.field(this,box,"実行時間の上限・秒（0 = 制限なし）",""+(plan.timeLimitMs/1000),false);
        EditText delay=Ui.field(this,box,"開始までの待ち時間（ms）",""+plan.startDelayMs,false);
        EditText gap=Ui.field(this,box,"1周完了後の待機（ms）",""+plan.cycleGapMs,false);
        Ui.gap(box,8);box.addView(Ui.text(this,"速度は各操作の「タップ間隔」で指定します。",12,Ui.ACCENT));
        Switch batch=Ui.toggle(this,box,"高速バッチ送信",plan.fastBatch);box.addView(Ui.text(this,"短い連打をまとめて送信。停止時に短い送信済み区間が完了する場合があります。",12,Ui.MUTED));
        EditText timing=Ui.field(this,box,"間隔のばらつき ±ms（0 = 固定）",""+plan.timingJitterMs,false);
        EditText pos=Ui.field(this,box,"タップ・長押し位置のばらつき ±px",""+plan.positionJitterPx,false);
        Switch screen=Ui.toggle(this,box,"実行中は画面を点灯したままにする",plan.keepScreenOn),volume=Ui.toggle(this,box,"音量キーで停止",plan.volumeStop);
        TextView error=Ui.text(this,"",12,Ui.RED);box.addView(error);ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog d=new AlertDialog.Builder(this,R.style.AppTheme).setTitle("実行設定").setView(scroll).setNegativeButton("閉じる",null).setPositiveButton("保存",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{
            Plan p=plan.copy();p.cycles=Ui.number(cycles,"周回数");long sec=Ui.number(seconds,"実行時間");Plan.range(sec,0,86400,"実行時間（秒）");p.timeLimitMs=sec*1000;p.startDelayMs=Ui.number(delay,"開始待ち");p.cycleGapMs=Ui.number(gap,"周回後待機");
            long t=Ui.number(timing,"時間のばらつき"),r=Ui.number(pos,"位置のばらつき");Plan.range(t,0,60000,"時間のばらつき");Plan.range(r,0,200,"位置のばらつき");p.timingJitterMs=(int)t;p.positionJitterPx=(int)r;p.fastBatch=batch.isChecked();p.keepScreenOn=screen.isChecked();p.volumeStop=volume.isChecked();store.save(p);d.dismiss();render();
        }catch(Exception e){error.setText(e.getMessage());}}));d.show();
    }
    private void templates(){String[] titles={"高速連打（20ms指定）","2点を交互にタップ","一定間隔でスクロール","長押し → スライド","ピンチで拡大"};new AlertDialog.Builder(this,R.style.AppTheme).setTitle("新しいパターンとして追加").setItems(titles,(d,i)->{
        if(store.all().size()>=50){Ui.toast(this,"パターンは50件までです");return;}Plan p=new Plan();p.name=titles[i];
        if(i==0){Plan.Step a=Plan.Step.create(Plan.Kind.TAP);a.gapMs=20;p.steps.add(a);}
        if(i==1){Plan.Step a=Plan.Step.create(Plan.Kind.TAP);a.x=.35;a.gapMs=150;p.steps.add(a);Plan.Step b=a.copy();b.x=.65;p.steps.add(b);}
        if(i==2){Plan.Step a=Plan.Step.create(Plan.Kind.SWIPE);a.y=.75;a.endY=.3;a.gapMs=1500;p.steps.add(a);}
        if(i==3){Plan.Step a=Plan.Step.create(Plan.Kind.HOLD);a.durationMs=1000;a.gapMs=200;p.steps.add(a);Plan.Step b=Plan.Step.create(Plan.Kind.SWIPE);b.y=.65;b.endY=.3;b.gapMs=500;p.steps.add(b);}
        if(i==4){p.steps.add(Plan.Step.create(Plan.Kind.PINCH_OUT));p.cycles=1;}
        store.save(p);render();
    }).show();}
    private void exportPlan(){try{exportData=PlanCodec.exportPlan(plan);Intent in=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"TapPilot-"+plan.name.replaceAll("[^\\p{L}\\p{N} _-]","_")+".json");startActivityForResult(in,EXPORT);}catch(Exception e){Ui.toast(this,e.getMessage());}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(request==EXPORT){if(exportData==null)throw new IOException("書き出しデータが失われました。もう一度実行してください");try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("書き込み先を開けません");out.write(exportData.getBytes(StandardCharsets.UTF_8));}Ui.toast(this,"JSONを書き出しました");}
            if(request==IMPORT){if(store.all().size()>=50)throw new IOException("パターンは50件までです");try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(in==null)throw new IOException("ファイルを開けません");byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>1048576)throw new IOException("ファイルは1MB以内です");out.write(b,0,n);}Plan p=PlanCodec.importPlan(new String(out.toByteArray(),StandardCharsets.UTF_8));store.save(p);render();Ui.toast(this,"パターンを追加しました");}}
        }catch(Exception e){Ui.toast(this,"処理できません: "+e.getMessage());}
    }
    private void help(){new AlertDialog.Builder(this,R.style.AppTheme).setTitle("使い方")
        .setMessage("1.「画面操作を有効化」でユーザー補助のTapPilotをON。\n2. フローティング操作を開く。\n3. 対象アプリに切り替え、＋から操作を追加。タップなら位置を1回タップ、スライドなら線を描く。\n4. 番号のドラッグで移動、番号をタップして間隔や回数を編集。\n5. ▶で開始、Ⅱで一時停止、■か音量キーで停止。パネル上部はドラッグで移動。\n\n周回数は手順全体の回数です。操作ごとの繰り返し回数とは別です。\n一時停止からの再開では、中断した操作を最初からやり直します。\n画面回転や折りたたみ後は座標を確認してください。\n\nAPK版で有効化が制限される場合：Android設定 → アプリ → TapPilot → 右上メニュー → 制限付き設定を許可。その後ユーザー補助を設定。\n通知は停止ボタンを通知欄に表示するためです。通知を許可しなくてもパネルを使えます。\n\n最小2msの設定は実際の500タップ/秒を保証しません。パネルの速度はジェスチャー送信完了数であり、対象アプリが受け付けた回数ではありません。")
        .setPositiveButton("閉じる",null).show();}
}
