#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
SDK_DIR="${ANDROID_HOME:-/opt/android-sdk}"
CP="$SDK_DIR/platforms/android-35/android.jar:$PWD/build/sdk/api.jar:$NEWSOUL_PUBLIC_CLASSES"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" tests/location-stubs/android/os/{Looper,Handler}.java tests/privacy-stubs/android/util/Log.java tests/privacy-stubs/dev/local/newsoul/SettingsUi.java tests/privacy-stubs/dev/local/newsoul/PluginPreferences.java \
    app/src/main/java/dev/local/newsoul/{PrivacyPhotoControls,PhotoResourceLease}.java tests/PrivacyRepeatControlsTest.java
java -cp "$OUT:$CP" dev.local.newsoul.PrivacyRepeatControlsTest
