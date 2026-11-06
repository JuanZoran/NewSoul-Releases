#!/usr/bin/env bash
# Offline JVM checks. Supply a real org.json JAR; android.jar contains stubs.
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
JSON_JAR="${JSON_TEST_JAR:-$PWD/build/test-libs/json-20240303.jar}"
TEST_OUT="$NEWSOUL_TEST_TMP/test_out"
mkdir -p "$TEST_OUT"
javac -encoding UTF-8 -source 17 -target 17 -cp "$JSON_JAR" -d "$TEST_OUT" \
    app/src/main/java/dev/local/newsoul/AiModelConfig.java \
    app/src/main/java/dev/local/newsoul/AiReplyClient.java \
    app/src/main/java/dev/local/newsoul/AiApiKeyProbe.java tests/AiApiKeyProbeTest.java \
    tests/AiCustomTransportTest.java \
    app/src/main/java/dev/local/newsoul/AiReplyRules.java \
    app/src/main/java/dev/local/newsoul/AiCandidateTextRules.java \
    app/src/main/java/dev/local/newsoul/AiMessageStatus.java \
    app/src/main/java/dev/local/newsoul/AiConversationRules.java \
    app/src/main/java/dev/local/newsoul/AiReplyPrompt.java \
    app/src/main/java/dev/local/newsoul/AiContextRules.java \
    app/src/main/java/dev/local/newsoul/AiMessageContextRules.java tests/AiMessageContextRulesTest.java \
    app/src/main/java/dev/local/newsoul/AiRequestContext.java tests/AiRequestContextTest.java tests/AiRequestBudgetTest.java \
    app/src/main/java/dev/local/newsoul/AiSuggestionCache.java tests/AiSuggestionCacheTest.java \
    app/src/main/java/dev/local/newsoul/AiOfficialSuggestionGate.java tests/AiOfficialSuggestionGateTest.java \
    app/src/main/java/dev/local/newsoul/LocalHistoryRules.java \
    app/src/main/java/dev/local/newsoul/LocalChatHistory.java tests/LocalChatHistoryTest.java \
    tests/ai-history-fixtures/cn/soulapp/imlib/database/ChatDbManager.java \
    app/src/main/java/dev/local/newsoul/AiImageSelectionRules.java \
    app/src/main/java/dev/local/newsoul/AiStickerRules.java \
    tests/AiStickerRulesTest.java \
    app/src/main/java/dev/local/newsoul/AiSharedPostRules.java \
    tests/AiMediaContextTest.java \
    app/src/main/java/dev/local/newsoul/AiSendRules.java \
    tests/AiSendRulesTest.java \
    tests/AiReplyClientTest.java tests/AiReplyRulesTest.java tests/AiReplyLayoutRecoveryTest.java \
    tests/AiContextRulesTest.java tests/LocalHistoryRulesTest.java \
    app/src/main/java/dev/local/newsoul/AiTimingRules.java tests/AiTimingRulesTest.java \
    tests/AiConversationRulesTest.java tests/AiReplyPromptTest.java tests/AiCacScenariosTest.java tests/AiMessageStatusTest.java \
    app/src/main/java/dev/local/newsoul/AiWritingRules.java \
    tests/AiWritingRulesTest.java \
    app/src/main/java/dev/local/newsoul/AiMaterialRules.java tests/AiMaterialRulesTest.java \
    tests/AiModelConfigTest.java
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiModelConfigTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiReplyClientTest --json
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiApiKeyProbeTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiCustomTransportTest
# Keep Android configuration substitutes isolated from the Android-free protocol tests.
CONFIG_OUT="$NEWSOUL_TEST_TMP/config_out"
mkdir -p "$CONFIG_OUT"
javac -encoding UTF-8 -source 17 -target 17 -cp "$TEST_OUT:$JSON_JAR:$NEWSOUL_HOST_CP" -d "$CONFIG_OUT" \
    tests/ai-config-stubs/android/content/Context.java \
    tests/ai-config-stubs/dev/local/newsoul/PluginPreferences.java \
    app/src/main/java/dev/local/newsoul/AiReplyConfig.java tests/AiCustomConfigTest.java
java -cp "$CONFIG_OUT:$TEST_OUT:$JSON_JAR:$NEWSOUL_HOST_CP" dev.local.newsoul.AiCustomConfigTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiReplyRulesTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiContextRulesTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.LocalHistoryRulesTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.LocalChatHistoryTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiConversationRulesTest
java -cp "$TEST_OUT:$JSON_JAR:app/src/main/resources" dev.local.newsoul.AiReplyPromptTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiSendRulesTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiMessageStatusTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiMediaContextTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiStickerRulesTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiWritingRulesTest

java -cp "$TEST_OUT:$JSON_JAR:app/src/main/resources" dev.local.newsoul.AiCacScenariosTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiReplyLayoutRecoveryTest

java -cp "$TEST_OUT:$JSON_JAR:app/src/main/resources" dev.local.newsoul.AiMaterialRulesTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiTimingRulesTest

java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiMessageContextRulesTest
java -cp "$TEST_OUT:$JSON_JAR:app/src/main/resources" dev.local.newsoul.AiRequestContextTest
java -cp "$TEST_OUT:$JSON_JAR:app/src/main/resources" dev.local.newsoul.AiRequestBudgetTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiSuggestionCacheTest
java -cp "$TEST_OUT:$JSON_JAR" dev.local.newsoul.AiOfficialSuggestionGateTest
