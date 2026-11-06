#!/usr/bin/env bash
# Source from a runner after changing to the repository root.
# Complete regression sessions share only freshly compiled production classes.
source tests/test-workspace.sh
if [[ -z "${NEWSOUL_REGRESSION_SESSION:-}" ]]; then
    python3 scripts/prepare-test-deps.py
fi
NEWSOUL_HOST_CP="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-35/android.jar:$PWD/build/sdk/api.jar:$PWD/build/deps/*"
newsoul_compile_public() {
    NEWSOUL_PUBLIC_CLASSES="${NEWSOUL_REGRESSION_SESSION:-$NEWSOUL_TEST_TMP}/public-classes"
    if [[ -n "${NEWSOUL_REGRESSION_SESSION:-}" && -f "$NEWSOUL_REGRESSION_SESSION/public-ready" ]]; then
        return
    fi
    mkdir -p "$NEWSOUL_PUBLIC_CLASSES"
    javac -encoding UTF-8 -source 17 -target 17 -cp "$NEWSOUL_HOST_CP" \
        -d "$NEWSOUL_PUBLIC_CLASSES" app/src/main/java/dev/local/newsoul/*.java
    if [[ -n "${NEWSOUL_REGRESSION_SESSION:-}" ]]; then
        chmod -R a-w "$NEWSOUL_PUBLIC_CLASSES"
        touch "$NEWSOUL_REGRESSION_SESSION/public-ready"
    fi
}

newsoul_compile_repeat_fixtures() {
    local fixtures="$NEWSOUL_TEST_TMP/fixtures/cn/soulapp/imlib/msg/chat"
    local classes="$NEWSOUL_TEST_TMP/repeat-fixture-classes"
    mkdir -p "$classes"
    python3 tests/prepare-repeat-fixtures.py "$fixtures"
    javac -encoding UTF-8 -cp "$NEWSOUL_HOST_CP" -d "$classes" "$fixtures"/*.java
    NEWSOUL_PUBLIC_CLASSES="$classes:$NEWSOUL_PUBLIC_CLASSES"
}
