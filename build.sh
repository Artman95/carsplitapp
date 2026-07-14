#!/bin/bash
# Self-contained build for the "Мультиокно" (navisplit) app.
# Uses the bundled toolchain in ./toolchain — no system JDK/Android SDK needed.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
AB="$DIR/toolchain"
APP="$DIR/app"
BT="$AB/android-14"
OUT="$DIR/build"

JAVA_HOME="$(ls -d "$AB"/jdk-*/Contents/Home 2>/dev/null | head -1)"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR="$(find "$AB" -name android.jar 2>/dev/null | head -1)"

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_JAR=$ANDROID_JAR"

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

echo "[1] aapt2 compile resources"
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "[2] aapt2 link (resources + manifest -> base.apk, gen R.java)"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest "$APP/AndroidManifest.xml" \
    --java "$OUT/gen" -o "$OUT/base.apk" "$OUT/res.zip"

echo "[3] javac (sources + R.java)"
"$JAVA_HOME/bin/javac" --release 8 -cp "$ANDROID_JAR" -d "$OUT/classes" \
    $(find "$APP/src" "$OUT/gen" -name '*.java')

echo "[4] d8 -> classes.dex"
"$BT/d8" --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex" \
    $(find "$OUT/classes" -name '*.class')

echo "[5] add classes.dex into apk"
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"
( cd "$OUT" && zip -q base.apk classes.dex )

echo "[6] zipalign"
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7] sign"
if [ ! -f "$AB/debug.keystore" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$AB/debug.keystore" -alias a \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android -dname "CN=Android Debug" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$AB/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$DIR/navisplit.apk" "$OUT/aligned.apk"

echo "BUILD_OK -> $DIR/navisplit.apk"
ls -la "$DIR/navisplit.apk"
