#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" app/src/main/java/dev/local/newsoul/FollowDisguiseRules.java tests/FollowDisguiseRulesTest.java
java -cp "$OUT" dev.local.newsoul.FollowDisguiseRulesTest
