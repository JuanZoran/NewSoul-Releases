#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/preference-classes"
mkdir -p "$OUT"
ANDROID_JAR="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar"
javac -encoding UTF-8 -cp "$ANDROID_JAR" -d "$OUT" \
  tests/preference-stubs/android/os/*.java \
  tests/preference-stubs/dev/local/newsoul/PreferenceRuntime.java \
  tests/plugin-preferences-stubs/android/content/Context.java \
  tests/plugin-preferences-stubs/dev/local/newsoul/VariantFeatures.java \
  app/src/main/java/dev/local/newsoul/PluginPreferences.java \
  app/src/main/java/dev/local/newsoul/TransactionalPreferences.java \
  app/src/main/java/dev/local/newsoul/PreferenceChanges.java \
  tests/TestWorkers.java tests/PreferenceChangesTest.java tests/PluginPreferencesTest.java
java -cp "$OUT:$ANDROID_JAR" dev.local.newsoul.PreferenceChangesTest
java -cp "$OUT:$ANDROID_JAR" dev.local.newsoul.PluginPreferencesTest
