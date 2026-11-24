#!/usr/bin/env bash
# Optional build route without Gradle. Uses only Android SDK tools and a Java compiler.
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_dir="${TAPPILOT_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
platform_jar="${TAPPILOT_ANDROID_JAR:-$sdk_dir/platforms/android-36/android.jar}"
build_tools="${TAPPILOT_BUILD_TOOLS:-$sdk_dir/build-tools/36.0.0}"
build_dir="$project_dir/build/manual"
output_dir="${TAPPILOT_OUTPUT:-$project_dir/build/outputs}"
test -f "$platform_jar" || { echo 'Set ANDROID_HOME, or TAPPILOT_ANDROID_JAR and TAPPILOT_BUILD_TOOLS.'; exit 1; }
mkdir -p "$build_dir/generated" "$build_dir/classes" "$build_dir/dex" "$output_dir" "$project_dir/signing"
export TAPPILOT_PROJECT_DIR="$project_dir" TAPPILOT_MANUAL_DIR="$build_dir"
python3 - <<'PY'
import os,pathlib
r=pathlib.Path(os.environ['TAPPILOT_PROJECT_DIR']);b=pathlib.Path(os.environ['TAPPILOT_MANUAL_DIR'])
s=(r/'app/src/main/AndroidManifest.xml').read_text()
(b/'AndroidManifest.xml').write_text(s.replace('<manifest xmlns:', '<manifest package="jp.tappilot.app" xmlns:',1))
PY
"$build_tools/aapt2" compile --dir "$project_dir/app/src/main/res" -o "$build_dir/resources.zip"
"$build_tools/aapt2" link -o "$build_dir/base.apk" -I "$platform_jar" --manifest "$build_dir/AndroidManifest.xml" --java "$build_dir/generated" --min-sdk-version 26 --target-sdk-version 36 --version-code 1 --version-name 1.0.0 "$build_dir/resources.zip"
find "$project_dir/app/src/main/java" "$build_dir/generated" -name '*.java' > "$build_dir/sources.txt"
if [ -n "${TAPPILOT_ECJ_JAR:-}" ]; then
    # ECJ uses host system modules for java.*; remove duplicate SDK stubs from its classpath.
    export TAPPILOT_PLATFORM_JAR="$platform_jar"
    python3 - <<'PY'
import os,pathlib,zipfile
b=pathlib.Path(os.environ['TAPPILOT_MANUAL_DIR'])
with zipfile.ZipFile(os.environ['TAPPILOT_PLATFORM_JAR']) as src,zipfile.ZipFile(b/'android-api.jar','w',zipfile.ZIP_DEFLATED) as dst:
 for item in src.infolist():
  if not item.filename.startswith(('java/','javax/','org/w3c/','org/xml/')):dst.writestr(item,src.read(item))
PY
    java -jar "$TAPPILOT_ECJ_JAR" -17 -proc:none -encoding UTF-8 -nowarn -classpath "$build_dir/android-api.jar" -d "$build_dir/classes" @"$build_dir/sources.txt"
else
    javac -source 17 -target 17 -encoding UTF-8 -classpath "$platform_jar" -d "$build_dir/classes" @"$build_dir/sources.txt"
fi
python3 - <<'PY'
import os,pathlib,zipfile
b=pathlib.Path(os.environ['TAPPILOT_MANUAL_DIR'])
with zipfile.ZipFile(b/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted((b/'classes').rglob('*.class')):z.write(p,p.relative_to(b/'classes'))
PY
java -cp "$build_tools/lib/d8.jar" com.android.tools.r8.D8 --lib "$platform_jar" --min-api 26 --output "$build_dir/dex" "$build_dir/classes.jar"
python3 - <<'PY'
import os,pathlib,zipfile,shutil
b=pathlib.Path(os.environ['TAPPILOT_MANUAL_DIR']);shutil.copyfile(b/'base.apk',b/'uncompressed.apk')
with zipfile.ZipFile(b/'uncompressed.apk','a',zipfile.ZIP_DEFLATED) as z:
 for p in sorted((b/'dex').glob('*.dex')):z.write(p,p.name)
PY
"$build_tools/zipalign" -f 4 "$build_dir/uncompressed.apk" "$build_dir/aligned.apk"
keystore="$project_dir/signing/tappilot-debug.keystore"
if [ ! -f "$keystore" ]; then
    keytool -genkeypair -keystore "$keystore" -alias androiddebugkey -storepass android -keypass android -dname 'CN=TapPilot Development,O=Personal,C=JP' -keyalg RSA -keysize 3072 -validity 10000
fi
java -jar "$build_tools/lib/apksigner.jar" sign --ks "$keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$output_dir/TapPilot-1.0.0.apk" "$build_dir/aligned.apk"
java -jar "$build_tools/lib/apksigner.jar" verify --verbose "$output_dir/TapPilot-1.0.0.apk"
echo "$output_dir/TapPilot-1.0.0.apk"
