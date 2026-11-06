#!/usr/bin/env bash
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO/build/debug-decrypt"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
    "$REPO/app/src/main/java/dev/local/newsoul/DebugEnvelope.java" \
    "$REPO/scripts/DebugDecrypt.java"
exec java -cp "$OUT" dev.local.newsoul.DebugDecrypt "$@"
