#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
newsoul_compile_repeat_fixtures
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="$NEWSOUL_PUBLIC_CLASSES:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 tests/action-stubs/android/app/Activity.java tests/action-stubs/android/content/Intent.java \
 tests/action-stubs/android/view/View.java tests/action-stubs/android/view/ViewGroup.java tests/action-stubs/android/widget/Toast.java tests/action-stubs/android/widget/GridLayout.java tests/action-stubs/android/widget/LinearLayout.java \
 tests/fallback-stubs/android/content/Context.java tests/fallback-stubs/android/content/res/Resources.java \
 tests/fallback-stubs/android/util/Log.java tests/auto-read-stubs/android/os/SystemClock.java \
 tests/chat-menu-stubs/dev/local/newsoul/DebugDiagnostics.java tests/ChatMenuActionTest.java
java -cp "$OUT:$CP" dev.local.newsoul.ChatMenuActionTest
