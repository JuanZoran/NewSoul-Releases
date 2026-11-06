#!/usr/bin/env bash
# Shared isolation only; JDK-only runners do not bootstrap Android/Maven dependencies.
if [[ -n "${NEWSOUL_REGRESSION_SESSION:-}" ]]; then
    if [[ ! -f "$NEWSOUL_REGRESSION_SESSION/repository" ]] \
        || [[ "$(cat "$NEWSOUL_REGRESSION_SESSION/repository")" != "$PWD" ]]; then
        echo 'Invalid regression session for this repository' >&2
        exit 1
    fi
fi
NEWSOUL_TEST_TMP="$(mktemp -d "${TMPDIR:-/tmp}/newsoul-tests.XXXXXXXX")"
trap 'rm -rf -- "$NEWSOUL_TEST_TMP"' EXIT
