#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -d "$OUT" app/src/main/java/dev/local/newsoul/ProfileBookmarkStore.java tests/ProfileBookmarkStoreTest.java app/src/main/java/dev/local/newsoul/ProfileBookmarkTargetRules.java tests/ProfileBookmarkTargetRulesTest.java
java -cp "$OUT" dev.local.newsoul.ProfileBookmarkStoreTest

java -cp "$OUT" dev.local.newsoul.ProfileBookmarkTargetRulesTest
