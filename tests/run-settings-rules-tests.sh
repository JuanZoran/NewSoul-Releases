#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
OUT="$NEWSOUL_TEST_TMP/settings-rules"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
 app/src/main/java/dev/local/newsoul/{MenuPagerPolicy,SettingsSearchRules}.java \
 tests/{MenuPagerPolicyTest,SettingsSearchRulesTest}.java
java -cp "$OUT" dev.local.newsoul.MenuPagerPolicyTest
java -cp "$OUT" dev.local.newsoul.SettingsSearchRulesTest
