#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="build/test-libs/json-20240303.jar:$NEWSOUL_PUBLIC_CLASSES:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 $(find tests/feed-stubs -name '*.java' ! -path '*/android/os/Handler.java') \
 $(find tests/registration-stubs -name '*.java') tests/location-stubs/android/os/Looper.java \
 tests/location-stubs/dev/local/newsoul/{SettingsUi,PluginPreferences,DebugDiagnostics}.java \
 app/src/main/java/dev/local/newsoul/{RegistrationDaysFilter,RegistrationRangeRules,AccountAutomationRules,FeedFilterRules}.java \
 tests/{RegistrationDaysPendingTest,RegistrationRangeRulesTest,AccountAutomationRulesTest}.java
java -cp "$OUT:$CP" dev.local.newsoul.RegistrationDaysPendingTest
java -cp "$OUT:$CP" dev.local.newsoul.RegistrationRangeRulesTest
java -cp "$OUT:$CP" dev.local.newsoul.AccountAutomationRulesTest
