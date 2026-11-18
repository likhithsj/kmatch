#!/bin/bash
# Differential fuzz: random inputs -> RapidFuzz (C++ backend) and kmatch ->
# bit-exact comparison. Exits non-zero on any mismatch.
#
#   tools/differential-fuzz/run.sh [pairs] [seed]        # tests this working tree
#   KMATCH_VERSION=1.0.0 tools/differential-fuzz/run.sh  # tests a published artifact
#
# Needs: pip install rapidfuzz==3.14.5
set -euo pipefail
cd "$(dirname "$0")"
PAIRS="${1:-3000}"
SEED="${2:-1}"
WORK="${TMPDIR:-/tmp}/kmatch-differential-fuzz-$SEED"
mkdir -p "$WORK"
python3 generate_cases.py "$PAIRS" "$SEED" "$WORK"
../../gradlew -p runner run --args="$WORK/cases.tsv $WORK/actual.tsv" -q
python3 compare.py "$WORK"
