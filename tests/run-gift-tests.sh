#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/gift"
mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$NEWSOUL_HOST_CP" -d "$OUT" \
 tests/privacy-stubs/android/util/Log.java tests/gift-stubs/dev/local/newsoul/ChatExperienceControls.java \
 app/src/main/java/dev/local/newsoul/{ChatGateControls,ChatGateResponse,ChatGateRules,HostBindings,HostDecodedResponse,TeenagePopupResponse}.java \
 tests/{ChatGateControlsTest,ChatGateRulesTest,TeenagePopupResponseTest}.java
java -cp "$OUT:$NEWSOUL_HOST_CP" dev.local.newsoul.ChatGateRulesTest
java -cp "$OUT:$NEWSOUL_HOST_CP" dev.local.newsoul.ChatGateControlsTest
java -cp "$OUT:$NEWSOUL_HOST_CP" dev.local.newsoul.TeenagePopupResponseTest
