#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="$NEWSOUL_PUBLIC_CLASSES:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar:build/deps/*"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 $(find tests/feed-stubs -name '*.java' ! -path '*/android/view/View.java' ! -path '*/android/util/Log.java') \
 $(find tests/time-order-stubs -name '*.java') tests/location-stubs/android/os/Looper.java \
 tests/legacy-like-stubs/cn/soulapp/android/component/chat/fragment/{BaseConversationFragment,ConversationFragment}.java \
 tests/location-stubs/dev/local/newsoul/SettingsUi.java tests/location-stubs/dev/local/newsoul/PluginPreferences.java \
 app/src/main/java/dev/local/newsoul/{SquareTimeOrder,FeedTimeOrder}.java tests/SquareTimeOrderTest.java
java -cp "$OUT:$CP" dev.local.newsoul.SquareTimeOrderTest
