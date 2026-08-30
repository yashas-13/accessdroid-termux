#!/data/data/com.termux/files/usr/bin/bash
# build-accessdroid.sh — builds AccessDroid APK in Termux
set -euo pipefail

PROJ="/data/data/com.termux/files/home/bin/accessdroid"
OUT="$PROJ/build"
ANDROID_JAR=$(ls ~/android-sdk/platforms/android-*/android.jar 2>/dev/null | tail -1)
[[ -z "$ANDROID_JAR" ]] && { echo "✗ android.jar not found in ~/android-sdk/platforms/"; exit 1; }
ZIPALIGN=~/android-sdk/build-tools/35.0.0/zipalign
KEYSTORE="$HOME/.android/debug.keystore"

rm -rf "$OUT"
mkdir -p "$OUT"
cd "$PROJ"

echo "────────────────────────────────────────────"
echo " AccessDroid APK builder"
echo "────────────────────────────────────────────"

# 1. Compile resources → binary resource zip
echo "[1/7] Compile resources (aapt2 compile)..."
aapt2 compile --dir res -o "$OUT/res.zip" 2>&1
echo "     ✓ $OUT/res.zip"

# 2. Link resources → generates R.java AND builds resources into base APK
echo "[2/7] Link resources + generate R.java (aapt2 link)..."
aapt2 link \
    -o "$OUT/base.apk" \
    --manifest AndroidManifest.xml \
    -I "$ANDROID_JAR" \
    --java "$OUT/r_java" \
    "$OUT/res.zip" \
    2>&1
# aapt2 link puts R.java under <folder>/<package/path>/R.java
RJAVADIR=$(find "$OUT/r_java" -name R.java 2>/dev/null | head -1)
echo "     ✓ R.java → $RJAVADIR"

# 3. Compile Java + R.java
echo "[3/7] Compile Java sources..."
mkdir -p "$OUT/classes"
javac -encoding UTF-8 \
    -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$OUT/classes" \
    -classpath "$ANDROID_JAR" \
    "$PROJ/AccessibilityServiceImpl.java" \
    "${RJAVADIR}" 2>&1 | tail -20
echo "     ✓ compiled classes"
find "$OUT/classes" -name '*.class' | wc -l | xargs echo "     classes:"

# 4. Convert → classes.dex
echo "[4/7] Create DEX (d8)..."
mkdir -p "$OUT/dex"
d8 --output "$OUT/dex" \
    --lib "$ANDROID_JAR" \
    --min-api 24 \
    $(find "$OUT/classes" -name '*.class') 2>&1 | tail -5
echo "     ✓ $OUT/dex/classes.dex"

# 5. Embed classes.dex into base.apk
echo "[5/7] Embed classes.dex into APK..."
cp "$OUT/base.apk" "$OUT/unsigned.apk"
# Use zip to add the dex (base.apk already has resources)
cd "$OUT/dex"
zip -j "$OUT/unsigned.apk" classes.dex 2>&1 | tail -3
cd "$PROJ"
echo "     ✓ unsigned.apk (resources + dex)"

# 6. Sign (zipalign skipped – SDK binary isn't aarch64-compatible here)
echo "[6/7] Sign APK..."
if [[ ! -f "$KEYSTORE" ]]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -3
fi
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUT/AccessDroid.apk" \
    "$OUT/unsigned.apk" 2>&1
echo "     ✓ signed"

# Verify
echo ""
echo "────────────────────────────────────────────"
apksigner verify -v "$OUT/AccessDroid.apk" 2>&1 | head -10
echo "────────────────────────────────────────────"
aapt2 dump badging "$OUT/AccessDroid.apk" 2>&1 | grep -E 'package:|application-label:|sdkVersion:|uses-permission:|launchable-activity:' | head -12
echo "────────────────────────────────────────────"
echo ""
echo "✅ APK: $OUT/AccessDroid.apk"
echo ""
echo "Install:"
echo "  1. Copy APK: termux-open $OUT/AccessDroid.apk"
echo "     (opens Android package installer)"
echo "  2. Or: adb install -r $OUT/AccessDroid.apk"
echo ""
echo "Then enable:"
echo "  Settings → Accessibility → Downloaded services"
echo "  → AccessDroid → toggle ON"
echo "  → confirm in dialog"
echo ""
echo "Secret: termux-accessdroid-2025"
