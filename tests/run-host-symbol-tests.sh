#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/out"
CP="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/deps/*"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 app/src/main/java/dev/local/newsoul/{HostSymbolCache,HostDexRules,HostSymbols}.java \
 tests/host-symbol-stubs/com/walid/rxretrofit/HttpSubscriber.java \
 tests/HostSymbolCacheTest.java tests/HostReflectionTest.java
java -cp "$OUT:$CP" dev.local.newsoul.HostSymbolCacheTest
java -cp "$OUT:$CP" dev.local.newsoul.HostReflectionTest
