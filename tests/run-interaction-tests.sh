#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
CP="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar:build/deps/*"
javac -encoding UTF-8 -cp "$NEWSOUL_PUBLIC_CLASSES:$CP" -d "$OUT" tests/interaction-stubs/dev/local/newsoul/*.java tests/interaction-stubs/cn/soulapp/android/component/chat/fragment/*.java tests/location-stubs/dev/local/newsoul/DebugDiagnostics.java tests/auto-read-stubs/android/content/Context.java tests/privacy-stubs/android/util/Log.java tests/InteractionPrivacyControlsTest.java tests/InteractionPrivacyRulesTest.java
java -cp "$OUT:$NEWSOUL_PUBLIC_CLASSES:build/test-libs/json-20240303.jar:$CP" dev.local.newsoul.InteractionPrivacyControlsTest
java -cp "$OUT:$NEWSOUL_PUBLIC_CLASSES:build/test-libs/json-20240303.jar:$CP" dev.local.newsoul.InteractionPrivacyRulesTest
