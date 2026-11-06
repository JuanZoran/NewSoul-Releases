#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
JSON_JAR="${JSON_TEST_JAR:-$PWD/build/test-libs/json-20240303.jar}"
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT" \
  app/src/main/java/dev/local/newsoul/ConfigDefaults.java \
  app/src/main/java/dev/local/newsoul/FeedLabelStyleRules.java \
  app/src/main/java/dev/local/newsoul/AccountAutomationRules.java \
  app/src/main/java/dev/local/newsoul/ConfigTransferRules.java \
  app/src/main/java/dev/local/newsoul/ChatTimeRules.java \
  app/src/main/java/dev/local/newsoul/FeedKeywordRules.java tests/ConfigTransferRulesTest.java
java -cp "$OUT:$JSON_JAR" dev.local.newsoul.ConfigTransferRulesTest
