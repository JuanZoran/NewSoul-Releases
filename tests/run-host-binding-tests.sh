#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
OUT="$NEWSOUL_TEST_TMP/host-bindings"
mkdir -p "$OUT"
javac -encoding UTF-8 -source 17 -target 17 -d "$OUT" \
  app/src/main/java/dev/local/newsoul/HostBindings.java tests/HostBindingsTest.java
java -cp "$OUT" dev.local.newsoul.HostBindingsTest
