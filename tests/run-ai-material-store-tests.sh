#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/ai-material-classes"
mkdir -p "$OUT"
ANDROID_JAR="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar"
JSON_JAR="$PWD/build/test-libs/json-20240303.jar"
javac -encoding UTF-8 -cp "$ANDROID_JAR:$JSON_JAR" -d "$OUT" \
  tests/preference-stubs/android/os/*.java \
  tests/ai-material-stubs/android/content/Context.java \
  app/src/main/java/dev/local/newsoul/TransactionalPreferences.java \
  app/src/main/java/dev/local/newsoul/AiMaterialStore.java \
  app/src/main/java/dev/local/newsoul/AiMaterialRules.java \
  app/src/main/java/dev/local/newsoul/AiWritingRules.java \
  app/src/main/java/dev/local/newsoul/AiReplyPrompt.java \
  tests/AiMaterialStoreTest.java
java -cp "$OUT:$JSON_JAR:$ANDROID_JAR" dev.local.newsoul.AiMaterialStoreTest
