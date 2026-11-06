#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
CP="$NEWSOUL_PUBLIC_CLASSES:${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:build/sdk/api.jar:build/deps/*"
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$CP" -d "$OUT" $(find tests/like-stubs/android tests/profile-like-stubs -name '*.java') tests/location-stubs/dev/local/newsoul/DebugDiagnostics.java tests/ProfileMomentLifecycleTest.java
java -cp "$OUT:$CP" dev.local.newsoul.ProfileMomentLifecycleTest
