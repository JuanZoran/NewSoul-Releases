#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-workspace.sh
OUT="$NEWSOUL_TEST_TMP/out"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" app/src/main/java/dev/local/newsoul/VoiceLibraryStore.java tests/VoiceLibraryStoreTest.java tests/VoiceLibraryRecoveryTest.java
java -cp "$OUT" dev.local.newsoul.VoiceLibraryStoreTest
java -cp "$OUT" dev.local.newsoul.VoiceLibraryRecoveryTest
python3 tests/run-voice-recovery-ui-tests.py
