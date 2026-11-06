#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
newsoul_compile_repeat_fixtures
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="$NEWSOUL_PUBLIC_CLASSES:$NEWSOUL_HOST_CP"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 tests/forward-recipient-stubs/android/app/Activity.java \
 tests/forward-recipient-stubs/android/content/Intent.java \
 tests/forward-recipient-stubs/android/widget/TextView.java \
 tests/action-stubs/android/view/View.java tests/action-stubs/android/widget/Toast.java \
 tests/fallback-stubs/android/content/Context.java tests/fallback-stubs/android/content/res/Resources.java \
 tests/fallback-stubs/android/util/Log.java tests/auto-read-stubs/android/os/SystemClock.java \
 tests/chat-menu-stubs/dev/local/newsoul/DebugDiagnostics.java tests/MessageForwardRecipientTest.java
java -cp "$OUT:$CP" dev.local.newsoul.MessageForwardRecipientTest
