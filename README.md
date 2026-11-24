# TapPilot 1.0.0

Android向けの自動タップ・スライドアプリ。Root不要。Android 8.0（API 26）以降向け。インターネット通信・広告・外部ライブラリはありません。

## インストールして使う

1. `TapPilot-1.0.0.apk` をAndroid端末で開いてインストールします。インストール元アプリの許可を求められたら、その画面の案内に従います。
2. TapPilotを開き「画面操作を有効化」→ 説明に同意 → Androidのユーザー補助設定でTapPilotを有効にします。
3. 有効化が制限されている場合は、Android設定 → アプリ → TapPilot → 右上のメニュー →「制限付き設定を許可」を設定してから、ユーザー補助を有効にします。表示や場所は端末によって異なります。
4. アプリへ戻り「フローティング操作を開く」。通知を許可すると通知欄にも停止ボタンを表示します。拒否してもパネルを使えます。
5. 操作するアプリへ切り替え、パネルの「＋」から操作を追加します。タップなら場所を1回タップ、スライド・軌跡なら指で線を描いて指定します。
6. 番号をドラッグして位置を移動。番号をタップすると間隔・動作時間・回数を設定できます。
7. 「▶」で開始、「Ⅱ」で一時停止、「■」または音量キーで停止。「×」で停止してパネルを閉じます。

パネルの上部をドラッグすると移動できます。「−」で折りたたみ、「位置」でマーカーの表示を切り替えます。操作位置や軌跡がパネルに重なる場合は開始を止めるので、パネルを移動してください。

## 操作と機能

| 操作 | 内容 |
| --- | --- |
| タップ / 連打 | 最小2msの開始間隔を指定。押下時間と回数も設定可能 |
| ダブルタップ | 2回のタップ間隔を指定 |
| 長押し | 最大60秒の押下を指定 |
| スライド | 始点・終点と移動時間を指定 |
| 自由な軌跡 | 指で描いた線を再生。最大2,000点 |
| ピンチイン / アウト | 中心から外側へ線を引いて2本指の操作を指定 |
| 待機 | 手順の間に待ち時間を追加 |
| 戻る / ホーム / 最近のアプリ | Androidのシステム操作を手順に追加 |

- 1パターンに最大500件の操作を登録。各操作の繰り返し、無効化、複製、並べ替え、削除。
- 手順全体の周回数、開始待ち、周回後の待機、実行時間の上限。
- 一時停止と再開。中断した操作は再開時に最初から実行します。
- 高速バッチ送信、間隔のばらつき、タップ・長押し位置のばらつき。
- 実行中の画面点灯、音量キー停止、通知の停止ボタン。
- パターンの名前変更・複製・保存。JSONの読み込み・書き出し。新規パターンは50件まで。
- 連打、2点交互タップ、自動スクロール、長押し＋スライド、ピンチ拡大のプリセット。
- アプリ内の動作テスト画面。実際に届いたタップ・長押し・スライドを数え、ピンチ倍率を表示。
- 画面オフ・回転・画面サイズ変更で停止。折りたたみ後などは位置を再確認してください。

## 速度・回数の意味

タップ間隔は「押し始めから、次に押し始めるまで」です。操作ごとの回数と、手順全体の周回数は別です。例えば、タップ10回の操作を3周すると30回です。周回数0は停止するまで繰り返します。

**2msの設定は、実際の500タップ/秒を保証しません。** Androidのジェスチャー処理、端末、画面更新、対象アプリによって速度と受付回数が変わります。最初は20〜100ms程度で試し、取りこぼす場合は間隔や押下時間を増やしてください。

パネルは、完了したジェスチャーに含まれるタップ数と、実行中の時間から「送信完了タップ/秒」を表示します。対象アプリが受け付けた回数を読み取る機能ではありません。中断したバッチは完了数へ加算しません。

長押しやスライドは約120msずつの連続ジェスチャーに分割しています。停止すると次の送信を止め、継続中の指を離します。送信済み区間やAndroid側の処理待ちがあるため、停止の反映には遅延が生じる場合があります。

開始時の設定をコピーして実行します。実行中にメイン画面で編集した内容は、次の開始から反映します。画面座標は画面全体の割合で保存するため、画面サイズ変更後も割合は維持しますが、対象アプリのボタン位置が同じとは限りません。

## 権限と保存

ユーザー補助サービスで、指定した座標へのジェスチャー送信、システム操作、操作パネル表示、停止用の音量キー受信を行います。画面内容を取得する機能（canRetrieveWindowContent）は無効です。キーイベントは音量キーによる停止の判定に使い、入力内容を記録しません。`INTERNET`、一般のストレージ権限、`SYSTEM_ALERT_WINDOW` は要求しません。

パターンは端末内へ保存します。JSONファイルの保存先・読み込み元はAndroidのファイル選択画面で選べます。アンインストール前に残したいパターンをJSONへ書き出してください。

## ソースからビルド

Android Studioでこのフォルダを開き、JDK 17以上、Android SDK Platform 36、Build Tools 36.0.0を用意します。Gradle 8.13 / Android Gradle Plugin 8.11.1を固定しています。

```sh
bash gradlew :app:assembleDebug :app:lintDebug
```

Windowsの場合：

```bat
gradlew.bat :app:assembleDebug :app:lintDebug
```

APKの出力先は `app/build/outputs/apk/debug/app-debug.apk` です。今回の成果物は、同じソースをSDKのaapt2、Javaコンパイラ、D8、zipalign、apksignerでビルドしています。Gradle経由のlint実行結果は今回の検証結果に含めていません。

Gradleを使わない場合：

```sh
ANDROID_HOME=/path/to/android-sdk bash tools/build-apk.sh
```

JDKのjavacがない環境では、`TAPPILOT_ECJ_JAR=/path/to/ecj.jar` を指定してEclipse ECJを利用できます。SDKのパスは `TAPPILOT_ANDROID_JAR` と `TAPPILOT_BUILD_TOOLS` で個別指定も可能です。

GradleはAndroid標準のデバッグ署名を使います。手動ビルドでは初回に `signing/tappilot-debug.keystore` をローカル生成します。一般配布時は自分のリリース鍵で署名してください。

## テスト

```sh
bash tools/test-core.sh
```

実行順・周回数・停止・再開・時間制限などのホストテストを実行します。

`tests/instrumentation/DeviceSmokeTest.java` はAndroidのテスト画面へ実際にジェスチャーを送り、タップ数・連続スライド・長押し・ピンチなどを確認するテストです。アプリ本体には含まれません。使い捨てのエミュレータを対象にし、個人端末の設定へ自動適用する用途ではありません。

```sh
# tools/build-apk.sh の実行後、SDKパスとECJのパスを設定してビルド
bash tools/build-device-tests.sh
# テスト用AVDを用意し、ANDROID_AVD_HOME / TAPPILOT_AVDを指定して実行
bash tools/run-device-tests.sh
```

## 参照したAndroidの仕様

- [ジェスチャー送信](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,android.accessibilityservice.AccessibilityService.GestureResultCallback,android.os.Handler))
- [ジェスチャーの長さ・ストローク数](https://developer.android.com/reference/android/accessibilityservice/GestureDescription)
- [ユーザー補助オーバーレイ](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
- [制限付き設定の案内](https://support.google.com/android/answer/12623953?hl=ja)

## ライセンス

TapPilotの独自コードはMITライセンスです。Gradle Wrapperの条件は `THIRD_PARTY_NOTICES.md` を参照してください。 LICENSEを参照してください。
