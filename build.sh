#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  echo "先设置 ANDROID_HOME，指向 Android SDK。" >&2
  exit 1
fi
BT="$(echo "$SDK"/build-tools/* | awk '{print $1}')"
ANDROID_JAR="$(echo "$SDK"/platforms/android-*/android.jar | awk '{print $NF}')"
if [ ! -x "$BT/aapt2" ] || [ ! -f "$ANDROID_JAR" ]; then
  echo "需要 build-tools 和 platforms（建议 API 34）。" >&2
  exit 1
fi
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/gen" "$WORK/compiled"
"$BT/aapt2" compile --dir "$ROOT/res" -o "$WORK/compiled/res.zip"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest "$ROOT/AndroidManifest.xml" \
  --java "$WORK/gen" --min-sdk-version 26 --target-sdk-version 34 \
  -A "$ROOT/assets" \
  -o "$WORK/linked.apk" "$WORK/compiled/res.zip"
find "$ROOT/src" "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
javac --release 11 -encoding UTF-8 -classpath "$ANDROID_JAR:$ROOT/libs/jxl-2.6.12.jar:$ROOT/libs/tesseract.jar" -d "$WORK/classes" @"$WORK/sources.txt"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$WORK/dex" \
  "$ROOT/libs/jxl-2.6.12.jar" "$ROOT/libs/tesseract.jar" $(find "$WORK/classes" -name '*.class')
cp "$WORK/linked.apk" "$WORK/unsigned.apk"
python3 - "$WORK" "$ROOT" <<'PY'
import os, sys, zipfile
work, root = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(work + "/unsigned.apk", "a")
for name in sorted(os.listdir(work + "/dex")):
    if name.endswith(".dex"):
        z.write(work + "/dex/" + name, name)
jni = os.path.join(root, "jniLibs")
if os.path.isdir(jni):
    for abi in os.listdir(jni):
        folder = os.path.join(jni, abi)
        if not os.path.isdir(folder):
            continue
        for so in os.listdir(folder):
            if not so.endswith(".so"):
                continue
            info = zipfile.ZipInfo("lib/" + abi + "/" + so)
            info.compress_type = zipfile.ZIP_STORED
            with open(os.path.join(folder, so), "rb") as fh:
                z.writestr(info, fh.read())
z.close()
PY
"$BT/zipalign" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
KEY="$ROOT/debug.keystore"
if [ ! -f "$KEY" ]; then
  keytool -genkeypair -keystore "$KEY" -storepass android -keypass android \
    -alias kezhong -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Kezhong, OU=App, O=Kezhong, L=Local, ST=Local, C=CN"
fi
"$BT/apksigner" sign --ks "$KEY" --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/课钟.apk" "$WORK/aligned.apk"
"$BT/apksigner" verify "$ROOT/课钟.apk"
echo "已生成 $ROOT/课钟.apk"
