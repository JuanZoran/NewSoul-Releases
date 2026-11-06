#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/ip-classes"
mkdir -p "$OUT"
CP="build/test-libs/json-20240303.jar:$NEWSOUL_PUBLIC_CLASSES:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 tests/feed-stubs/android/content/Context.java \
 tests/registration-stubs/android/os/Handler.java tests/location-stubs/android/os/Looper.java \
 tests/ip-lookup-stubs/HostAccountHttp.java tests/registration-stubs/dev/local/newsoul/SquareFeedFilter.java \
 tests/location-stubs/dev/local/newsoul/{SettingsUi,PluginPreferences,DebugDiagnostics}.java \
 app/src/main/java/dev/local/newsoul/{SquareIpLocationLookup,SquareIpLocationRules}.java \
 tests/{SquareIpLocationLookupTest,SquareIpLocationRulesTest}.java
java -cp "$OUT:$CP" dev.local.newsoul.SquareIpLocationLookupTest
java -cp "$OUT:$CP" dev.local.newsoul.SquareIpLocationRulesTest
