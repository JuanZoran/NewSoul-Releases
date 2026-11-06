#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/out"
CP="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
  tests/chat-menu-stubs/dev/local/newsoul/DebugDiagnostics.java \
  app/src/main/java/dev/local/newsoul/ChatMenuTrace.java \
  app/src/main/java/dev/local/newsoul/ChatMenuDiagnostics.java \
  app/src/main/java/dev/local/newsoul/HostBindings.java \
  tests/TestWorkers.java tests/ChatMenuDiagnosticsTest.java
java -cp "$OUT:$CP" dev.local.newsoul.ChatMenuDiagnosticsTest
