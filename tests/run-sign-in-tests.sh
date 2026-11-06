#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/sign-in"
mkdir -p "$OUT"
CP="build/test-libs/json-20240303.jar"
javac -encoding UTF-8 -source 17 -target 17 -cp "$CP" -d "$OUT" \
 app/src/main/java/dev/local/newsoul/SignInTasks.java tests/SignInTasksTest.java
java -cp "$OUT:$CP" dev.local.newsoul.SignInTasksTest
