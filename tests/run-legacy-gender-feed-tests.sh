#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="build/test-libs/json-20240303.jar:$NEWSOUL_PUBLIC_CLASSES:$NEWSOUL_HOST_CP"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 $(find tests/feed-stubs -name '*.java' ! -path '*/android/os/Handler.java') \
 tests/registration-stubs/android/os/Handler.java tests/registration-stubs/dev/local/newsoul/HostAccountHttp.java \
 tests/feed-metadata-stubs/dev/local/newsoul/SquareTimeOrder.java tests/location-stubs/android/os/Looper.java \
 tests/location-stubs/dev/local/newsoul/{SettingsUi,PluginPreferences,DebugDiagnostics}.java \
 tests/legacy-like-stubs/cn/soulapp/android/component/chat/fragment/{BaseConversationFragment,ConversationFragment}.java \
 app/src/main/java/dev/local/newsoul/{SquareFeedFilter,RegistrationDaysFilter,FeedFilterRules,FeedKeywordRules,FeedContentRules,AccountAutomationRules}.java \
 tests/{CityFeedFilterTest,LegacyGenderFeedTest}.java
java -cp "$OUT:$CP" dev.local.newsoul.LegacyGenderFeedTest
