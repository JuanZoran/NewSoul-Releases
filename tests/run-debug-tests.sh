#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
    app/src/main/java/dev/local/newsoul/DebugEnvelope.java \
    app/src/main/java/dev/local/newsoul/DebugRecipient.java \
    app/src/main/java/dev/local/newsoul/DebugStore.java \
    scripts/DebugDecrypt.java tests/DebugDiagnosticsTest.java
java -cp "$OUT" dev.local.newsoul.DebugDiagnosticsTest "$@"
