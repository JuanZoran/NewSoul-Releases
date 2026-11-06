#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar:build/deps/*"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 $(find tests/auto-read-stubs -name '*.java') tests/privacy-stubs/android/util/Log.java \
 app/src/main/java/dev/local/newsoul/{AutoReadControls,AutoReadRules,ChatRecallRules,HostDexRules,HostBindings}.java \
 tests/AutoReadControlsTest.java tests/ChatRecallRulesTest.java
java -cp "$OUT:$CP" dev.local.newsoul.AutoReadControlsTest
java -cp "$OUT:$CP" dev.local.newsoul.ChatRecallRulesTest
