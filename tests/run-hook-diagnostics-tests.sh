#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
OUT="$NEWSOUL_TEST_TMP/diagnostics-classes"
mkdir -p "$OUT"
# Shadow edition readiness only; production Result and probe logic execute unchanged.
javac -encoding UTF-8 -cp "$NEWSOUL_PUBLIC_CLASSES:$NEWSOUL_HOST_CP" -d "$OUT" \
    tests/diagnostics-stubs/dev/local/newsoul/VariantFeatures.java \
    app/src/main/java/dev/local/newsoul/HookDiagnostics.java tests/HookDiagnosticsTest.java
java -cp "$OUT:$NEWSOUL_PUBLIC_CLASSES:$NEWSOUL_HOST_CP" dev.local.newsoul.HookDiagnosticsTest
