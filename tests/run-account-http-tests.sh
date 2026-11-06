#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
OUT="$NEWSOUL_TEST_TMP/account-http"
mkdir -p "$OUT"
CP="build/test-libs/json-20240303.jar:$NEWSOUL_HOST_CP"
javac -encoding UTF-8 -source 17 -target 17 -cp "$CP" -d "$OUT" \
 $(find tests/account-http-stubs -name '*.java') \
 tests/privacy-stubs/android/util/Log.java \
 tests/registration-stubs/android/os/Handler.java tests/location-stubs/android/os/Looper.java \
 tests/location-stubs/dev/local/newsoul/DebugDiagnostics.java \
 tests/registration-stubs/dev/local/newsoul/SquareFeedFilter.java \
 app/src/main/java/dev/local/newsoul/{HostBindings,HostAccountHttp,HostRequestScope,HostWelfareHttp,SignInTasks,SignInAutomation,AvatarRewardNotifications,AccountAutomationControls,AccountAutomationRules,RegistrationDaysFilter,SquareIpLocationLookup,FeedFilterRules,FeedMetadataFacts}.java \
 tests/AccountHttpLifecycleTest.java
java -cp "$OUT:$CP" dev.local.newsoul.AccountHttpLifecycleTest
java -cp "$OUT:$CP" dev.local.newsoul.AccountHttpLifecycleTest 6.37
