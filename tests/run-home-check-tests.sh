#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
NEWSOUL_HOME_TEST_DIR=$(mktemp -d "${TMPDIR:-/tmp}/newsoul-home-test.XXXXXXXX")
trap 'rm -rf -- "$NEWSOUL_HOME_TEST_DIR"' EXIT
javac -d "$NEWSOUL_HOME_TEST_DIR" app/src/main/java/dev/local/newsoul/{HomeCheck,ForegroundNotificationState}.java tests/{HomeCheckTest,ForegroundNotificationStateTest}.java
java -cp "$NEWSOUL_HOME_TEST_DIR" dev.local.newsoul.HomeCheckTest
java -cp "$NEWSOUL_HOME_TEST_DIR" dev.local.newsoul.ForegroundNotificationStateTest
