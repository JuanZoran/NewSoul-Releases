#!/usr/bin/env bash
# Optional rooted-device test. Uses an offscreen Surface only; no Activity or UI input.
set -euo pipefail
cd "$(dirname "$0")/.."
GLASS_SDK="${ANDROID_HOME:-/opt/android-sdk}"
GLASS_OUT=build/ui-appearance-probe
mkdir -p "$GLASS_OUT/classes" "$GLASS_OUT/dex" "$GLASS_OUT/images"
javac -cp "$GLASS_SDK/platforms/android-35/android.jar" -d "$GLASS_OUT/classes" \
 app/src/main/java/dev/local/newsoul/GlassLensRenderer.java \
 app/src/main/java/dev/local/newsoul/GlassCardBackground.java \
 app/src/main/java/dev/local/newsoul/UiAppearance.java tests/UiAppearanceRenderProbe.java
"$GLASS_SDK/build-tools/35.0.0/d8" --lib "$GLASS_SDK/platforms/android-35/android.jar" \
 --output "$GLASS_OUT/dex" "$GLASS_OUT"/classes/dev/local/newsoul/*.class
(cd "$GLASS_OUT/dex" && zip -q ../probe.jar classes.dex)
adb push "$GLASS_OUT/probe.jar" /data/local/tmp/newsoul-ui-appearance-probe.jar
adb shell su -c 'CLASSPATH=/data/local/tmp/newsoul-ui-appearance-probe.jar app_process /system/bin dev.local.newsoul.UiAppearanceRenderProbe /data/local/tmp/newsoul-appearance' | tee "$GLASS_OUT/result.txt"
rg -q "352 production role/state GPU scenes passed" "$GLASS_OUT/result.txt"
for style in LIQUID_GLASS FLAT MATTE ORIGINAL_GLASS_TABS; do
 for dark in false true; do
  adb exec-out su -c "cat /data/local/tmp/newsoul-appearance-$style-$dark.png" > "$GLASS_OUT/images/$style-$dark.png"
 done
done
