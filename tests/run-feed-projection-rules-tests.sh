#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="$NEWSOUL_PUBLIC_CLASSES:$NEWSOUL_HOST_CP"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 $(find tests/feed-stubs -name '*.java') tests/location-stubs/android/os/Looper.java \
 tests/location-stubs/dev/local/newsoul/{SettingsUi,PluginPreferences,DebugDiagnostics}.java \
 app/src/main/java/dev/local/newsoul/{SquareFeedFilter,FeedFilterRules,FeedKeywordRules}.java tests/FeedProjectionRulesTest.java
java -cp "$OUT:$CP" dev.local.newsoul.FeedProjectionRulesTest
