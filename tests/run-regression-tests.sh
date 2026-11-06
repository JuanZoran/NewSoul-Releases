#!/usr/bin/env bash
# Offline JVM regressions only: no signing, releases, device probes or model requests.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/prepare-test-deps.py
python3 tests/test_dependency_bootstrap.py
NEWSOUL_REGRESSION_SESSION="$(mktemp -d "${TMPDIR:-/tmp}/newsoul-regression.XXXXXXXX")"
export NEWSOUL_REGRESSION_SESSION
trap 'chmod -R u+w "$NEWSOUL_REGRESSION_SESSION"; rm -rf -- "$NEWSOUL_REGRESSION_SESSION"' EXIT
printf '%s\n' "$PWD" > "$NEWSOUL_REGRESSION_SESSION/repository"
runners=(
 source-compile config preference-changes home-check settings-rules update host-symbol host-binding host-ui-binding account-http sign-in restriction gift privacy repeat auto-read chat-menu chat-time
 city-feed city-like legacy-like legacy-gender-feed profile-like-lifecycle registration-pending ip-location time-order feed-projection-rules
 interaction location menu-action menu-fallback profile-bookmark follow-disguise chat-game
debug voice-library ai ai-material-store ai-send forward-recipient ad-binding hook-diagnostics
)
for runner in "${runners[@]}"; do
    echo "Regression: $runner"
    bash "tests/run-$runner-tests.sh"
done
for runner in settings-detach photo-dock-lifecycle native-switch frosted-menu feed-header chat-footnote media-transaction ip-location-lifecycle square-like-attachment home-lifecycle; do
    echo "Regression: $runner"
    python3 "tests/run-$runner-tests.py"
done
python3 tests/check-ui-appearance.py
python3 tests/test_home_launcher.py
python3 -m unittest discover -s tests -p 'test_*release.py'
python3 -m unittest discover -s tests -p 'test_telegram_feedback.py'
python3 tests/test_worker_failure_reporting.py
echo 'Offline regression suite passed'
