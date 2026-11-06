#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
SEND_TEST_OUT="$NEWSOUL_TEST_TMP/send_test_out"
mkdir -p "$SEND_TEST_OUT"
mapfile -t SEND_STUBS < <(find tests/ai-send-stubs -name '*.java')
javac -encoding UTF-8 -source 17 -target 17 -d "$SEND_TEST_OUT" "${SEND_STUBS[@]}" \
  app/src/main/java/dev/local/newsoul/HostBindings.java app/src/main/java/dev/local/newsoul/AiReplySender.java app/src/main/java/dev/local/newsoul/AiSendSequence.java \
  app/src/main/java/dev/local/newsoul/AiMessageStatus.java app/src/main/java/dev/local/newsoul/AiSendRules.java \
  tests/AiReplySenderTest.java tests/AiSendSequenceTest.java
java -cp "$SEND_TEST_OUT" dev.local.newsoul.AiReplySenderTest

java -cp "$SEND_TEST_OUT" dev.local.newsoul.AiSendSequenceTest
