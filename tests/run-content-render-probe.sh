#!/usr/bin/env bash
# Offscreen only: no Activity, input, app install or restart.
set -euo pipefail
cd "$(dirname "$0")/.."
CONTENT_SDK="${ANDROID_HOME:-/opt/android-sdk}"
CONTENT_OUT=build/content-probe
mkdir -p "$CONTENT_OUT/classes" "$CONTENT_OUT/dex"
javac -cp "$CONTENT_SDK/platforms/android-35/android.jar" -d "$CONTENT_OUT/classes" \
 app/src/main/java/dev/local/newsoul/SoftFilterBackground.java tests/FeedContentRenderProbe.java
"$CONTENT_SDK/build-tools/35.0.0/d8" --lib "$CONTENT_SDK/platforms/android-35/android.jar" \
 --output "$CONTENT_OUT/dex" "$CONTENT_OUT"/classes/dev/local/newsoul/*.class
(cd "$CONTENT_OUT/dex" && zip -q ../probe.jar classes.dex)
adb push "$CONTENT_OUT/probe.jar" /data/local/tmp/newsoul-content-probe.jar
adb shell su -c 'CLASSPATH=/data/local/tmp/newsoul-content-probe.jar app_process /system/bin dev.local.newsoul.FeedContentRenderProbe /data/local/tmp/newsoul-content.png'
adb exec-out su -c 'cat /data/local/tmp/newsoul-content.png' > "$CONTENT_OUT/render.png"
