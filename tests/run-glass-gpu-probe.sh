#!/usr/bin/env bash
# Optional rooted-device test. Uses an offscreen Surface only; no Activity or UI input.
set -euo pipefail
cd "$(dirname "$0")/.."
GLASS_SDK="${ANDROID_HOME:-/opt/android-sdk}"
GLASS_OUT=build/glass-gpu-probe
mkdir -p "$GLASS_OUT/classes" "$GLASS_OUT/dex" "$GLASS_OUT/images"
javac -cp "$GLASS_SDK/platforms/android-35/android.jar" -d "$GLASS_OUT/classes" \
 app/src/main/java/dev/local/newsoul/GlassLensRenderer.java \
 app/src/main/java/dev/local/newsoul/GlassCardBackground.java \
 app/src/main/java/dev/local/newsoul/UiAppearance.java tests/GlassGpuRenderProbe.java
"$GLASS_SDK/build-tools/35.0.0/d8" --lib "$GLASS_SDK/platforms/android-35/android.jar" \
 --output "$GLASS_OUT/dex" "$GLASS_OUT"/classes/dev/local/newsoul/*.class
(cd "$GLASS_OUT/dex" && zip -q ../probe.jar classes.dex)
adb push "$GLASS_OUT/probe.jar" /data/local/tmp/newsoul-glass-gpu-probe.jar
adb shell su -c 'CLASSPATH=/data/local/tmp/newsoul-glass-gpu-probe.jar app_process /system/bin dev.local.newsoul.GlassGpuRenderProbe /data/local/tmp/newsoul-gpu' | tee "$GLASS_OUT/result.txt"
for theme in dark light; do
 for frame in 0 1 2 3 4; do
  adb exec-out su -c "cat /data/local/tmp/newsoul-gpu-$theme-$frame.png" > "$GLASS_OUT/images/$theme-$frame.png"
 done
done
