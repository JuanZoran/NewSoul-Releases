#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
JSON_JAR="${JSON_TEST_JAR:-$PWD/build/test-libs/json-20240303.jar}"
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT" \
  app/src/main/java/dev/local/newsoul/LanzouUpdateClient.java tests/LanzouUpdateClientTest.java
java -cp "$OUT:$JSON_JAR" dev.local.newsoul.LanzouUpdateClientTest "$@"
