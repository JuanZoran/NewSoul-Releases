#!/usr/bin/env bash
# Compile the complete unified edition without signing or distribution.
set -euo pipefail
cd "$(dirname "$0")/.."
source tests/test-env.sh
newsoul_compile_public
echo 'Complete unified source compilation passed'
