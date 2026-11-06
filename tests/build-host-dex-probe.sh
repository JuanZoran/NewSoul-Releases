#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/prepare-dexkit-deps.py
OUT="$PWD/build/host-dex-probe"
BT="${ANDROID_HOME:-/opt/android-sdk}/build-tools/35.0.0"
mkdir -p "$OUT/classes" "$OUT/dex"
javac -encoding UTF-8 -source 17 -target 17 -cp "build/deps/*:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar" -d "$OUT/classes" \
 app/src/main/java/dev/local/newsoul/{HostDexRules,HostSymbolCache,HostSymbols}.java tests/HostDexProbe.java tests/HostInstallProbe.java
jar cf "$OUT/probe-classes.jar" -C "$OUT/classes" .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 \
 --lib "${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar" \
 --output "$OUT/dex" "$OUT/probe-classes.jar" build/deps/dexkit.jar \
 build/deps/flatbuffers-java-23.5.26.jar build/deps/kotlin-stdlib-1.9.24.jar build/deps/annotations-13.0.jar
(cd "$OUT/dex" && zip -q ../probe.jar classes*.dex)
echo "$OUT/probe.jar"

# Exercise the same shrinker and JNI keep rules used by the shipped module.
mkdir -p "$OUT/r8"
echo '-keep class dev.local.newsoul.Host*Probe { public static void main(java.lang.String[]); }' > "$OUT/probe.pro"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 26 \
 --lib "${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar" \
 --pg-conf "$OUT/probe.pro" --pg-conf build/deps/dexkit-proguard.pro \
 --output "$OUT/r8" "$OUT/probe-classes.jar" build/deps/dexkit.jar \
 build/deps/flatbuffers-java-23.5.26.jar build/deps/kotlin-stdlib-1.9.24.jar build/deps/annotations-13.0.jar
(cd "$OUT/r8" && zip -q ../probe-r8.jar classes*.dex)
