#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "$0")/.." && pwd)"
test_dir="$root_dir/build/device-tests"
sdk_dir="${TAPPILOT_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
platform_jar="${TAPPILOT_ANDROID_JAR:-$sdk_dir/platforms/android-36/android.jar}"
build_tools="${TAPPILOT_BUILD_TOOLS:-$sdk_dir/build-tools/36.0.0}"
mkdir -p "$test_dir/classes" "$test_dir/dex"
java -jar "$TAPPILOT_ECJ_JAR" -17 -proc:none -encoding UTF-8 -nowarn -classpath "$root_dir/build/manual/android-api.jar:$root_dir/build/manual/classes" -d "$test_dir/classes" "$root_dir/tests/instrumentation/DeviceSmokeTest.java"
export TAPPILOT_DEVICE_TEST_DIR="$test_dir"
python3 - <<'PY'
import pathlib,os,zipfile
d=pathlib.Path(os.environ['TAPPILOT_DEVICE_TEST_DIR'])
with zipfile.ZipFile(d/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted((d/'classes').rglob('*.class')):z.write(p,p.relative_to(d/'classes'))
PY
java -cp "$build_tools/lib/d8.jar" com.android.tools.r8.D8 --lib "$platform_jar" --classpath "$root_dir/build/manual/classes.jar" --min-api 26 --output "$test_dir/dex" "$test_dir/classes.jar"
"$build_tools/aapt2" link -o "$test_dir/base.apk" -I "$platform_jar" --manifest "$root_dir/tests/instrumentation/AndroidManifest.xml"
python3 - <<'PY'
import pathlib,os,zipfile
d=pathlib.Path(os.environ['TAPPILOT_DEVICE_TEST_DIR'])
with zipfile.ZipFile(d/'base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(d/'dex/classes.dex','classes.dex')
PY
"$build_tools/zipalign" -f 4 "$test_dir/base.apk" "$test_dir/aligned.apk"
java -jar "$build_tools/lib/apksigner.jar" sign --ks "$root_dir/signing/tappilot-debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --out "$test_dir/TapPilot-tests.apk" "$test_dir/aligned.apk"
echo "$test_dir/TapPilot-tests.apk"
