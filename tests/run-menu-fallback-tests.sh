#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
# Build production classes and payload fixtures first.
newsoul_compile_public
newsoul_compile_repeat_fixtures
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="$NEWSOUL_PUBLIC_CLASSES:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 tests/fallback-stubs/android/app/Activity.java tests/fallback-stubs/android/content/Context.java \
 tests/fallback-stubs/android/content/res/Resources.java tests/fallback-stubs/android/view/View.java \
 tests/fallback-stubs/android/util/Log.java tests/chat-menu-stubs/dev/local/newsoul/DebugDiagnostics.java \
 tests/TestWorkers.java tests/ChatMenuFallbackTest.java
java -cp "$OUT:$CP" dev.local.newsoul.ChatMenuFallbackTest
