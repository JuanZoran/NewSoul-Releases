#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="$NEWSOUL_PUBLIC_CLASSES:$NEWSOUL_HOST_CP"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" \
 $(find tests/game-stubs -name '*.java') \
 tests/auto-read-stubs/android/content/Context.java tests/location-stubs/android/os/Looper.java \
 tests/privacy-stubs/android/util/Log.java tests/location-stubs/dev/local/newsoul/SettingsUi.java tests/location-stubs/dev/local/newsoul/PluginPreferences.java \
 app/src/main/java/dev/local/newsoul/{ChatGames,GameSendGuard}.java tests/ChatGamesGuardTest.java
java -cp "$OUT:$CP" dev.local.newsoul.ChatGamesGuardTest
