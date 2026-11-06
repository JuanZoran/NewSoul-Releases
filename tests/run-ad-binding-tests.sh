#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" tests/ad-stubs/dev/local/newsoul/HostBindings.java \
 app/src/main/java/dev/local/newsoul/HostAdBindings.java tests/HostAdBindingsTest.java \
 app/src/main/java/dev/local/newsoul/AdFeedRules.java tests/AdFeedRulesTest.java
java -cp "$OUT" dev.local.newsoul.HostAdBindingsTest
java -cp "$OUT" dev.local.newsoul.AdFeedRulesTest
