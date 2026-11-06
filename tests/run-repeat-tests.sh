#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
newsoul_compile_repeat_fixtures
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT/classes"
CP="$NEWSOUL_PUBLIC_CLASSES:$NEWSOUL_HOST_CP"
javac -encoding UTF-8 -cp "$CP" -d "$OUT/classes" \
    tests/RepeatMessageRulesTest.java tests/RepeatPayloadTest.java tests/MessageForwardRequestTest.java tests/MessageForwardPayloadTest.java tests/VoiceLibraryBindingTest.java tests/PostSharePayloadTest.java
java -cp "$OUT/classes:$CP" dev.local.newsoul.RepeatMessageRulesTest
java -cp "$OUT/classes:$CP" dev.local.newsoul.RepeatPayloadTest
java -cp "$OUT/classes:$CP" dev.local.newsoul.MessageForwardRequestTest

java -cp "$OUT/classes:$CP" dev.local.newsoul.VoiceLibraryBindingTest

java -cp "$OUT/classes:$CP" dev.local.newsoul.MessageForwardPayloadTest

java -cp "$OUT/classes:$CP" dev.local.newsoul.PostSharePayloadTest
