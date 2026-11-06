#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/out"
CP="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:$PWD/build/sdk/api.jar:$PWD/build/deps/*"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" tests/privacy-stubs/android/util/Log.java \
 tests/restriction-stubs/dev/local/newsoul/ChatExperienceControls.java \
 app/src/main/java/dev/local/newsoul/{PostShareControls,SocialScoreControls,HostBindings,HostSymbols,HostDexRules,HostSymbolCache}.java tests/RestrictionControlsTest.java
java -cp "$OUT:$CP" dev.local.newsoul.RestrictionControlsTest
