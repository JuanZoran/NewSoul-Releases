#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="$NEWSOUL_PUBLIC_CLASSES:build/test-libs/json-20240303.jar:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar:build/deps/*"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 tests/location-stubs/android/content/Context.java tests/location-stubs/android/os/{Handler,Looper}.java \
 tests/location-stubs/dev/local/newsoul/{SettingsUi,PluginPreferences,HostSymbols,LocationOverridePicker}.java \
 tests/location-stubs/dev/local/newsoul/DebugDiagnostics.java \
 app/src/main/java/dev/local/newsoul/{LocationOverrideControls,LocationOverridePoint,HostDexRules}.java \
 tests/LocationOverrideTest.java
java -cp "$OUT:$CP" dev.local.newsoul.LocationOverrideTest
UI_OUT="$NEWSOUL_TEST_TMP/ui_out"
mkdir -p "$UI_OUT"
javac -encoding UTF-8 -cp "$CP" -d "$UI_OUT" \
 $(find tests/location-ui-stubs -name '*.java') \
 tests/preference-stubs/android/os/*.java \
 tests/location-stubs/dev/local/newsoul/DebugDiagnostics.java \
 app/src/main/java/dev/local/newsoul/{LocationCityEntry,LocationOverridePicker,LocationOverridePoint}.java \
 tests/LocationUiTest.java
java -cp "$UI_OUT:$CP" dev.local.newsoul.LocationUiTest
