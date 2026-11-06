#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$PWD/build/legacy-host-probe"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
mkdir -p "$OUT/classes" "$OUT/dex"
javac -encoding UTF-8 -source 17 -target 17 -cp "$SDK/platforms/android-35/android.jar" -d "$OUT/classes" \
 app/src/main/java/dev/local/newsoul/{HostBindings,HostMediaBindings,HostShareBindings,HostMessageDecoration,HostAdBindings,ChatGateResponse,ChatGateRules,HostDecodedResponse,TeenagePopupResponse}.java \
 tests/LegacyHostProbe.java
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
java -cp "$SDK/build-tools/35.0.0/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 \
 --lib "$SDK/platforms/android-35/android.jar" --output "$OUT/dex" "$OUT/classes.jar"
(cd "$OUT/dex" && zip -q ../probe.jar classes.dex)
echo "$OUT/probe.jar"
