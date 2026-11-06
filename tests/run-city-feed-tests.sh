#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
rm -rf "$OUT"
mkdir -p "$OUT"
HOST_CP="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar:build/deps/*"
CP="$NEWSOUL_PUBLIC_CLASSES:$HOST_CP"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 $(find tests/feed-stubs -name '*.java') tests/location-stubs/android/os/Looper.java \
 $(find tests/feed-metadata-stubs -name '*.java') \
 tests/location-stubs/dev/local/newsoul/{SettingsUi,PluginPreferences,DebugDiagnostics}.java \
 app/src/main/java/dev/local/newsoul/{SquareFeedFilter,FeedFilterRules,FeedKeywordRules,FeedContentRules}.java \
 tests/{CityFeedFilterTest,FeedFilterRulesTest,FeedKeywordRulesTest,FeedContentRulesTest}.java
for test in CityFeedFilterTest FeedFilterRulesTest FeedKeywordRulesTest FeedContentRulesTest; do java -cp "$OUT:$CP" dev.local.newsoul.$test; done
