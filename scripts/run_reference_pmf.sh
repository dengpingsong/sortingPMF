#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PMF_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(CDPATH= cd -- "$PMF_ROOT/../.." && pwd)

"$SCRIPT_DIR/build_reference_pmf.sh" >/dev/null

cd "$REPO_ROOT"

JAVA_OPTS=${REFERENCE_PMF_JAVA_OPTS:-}

exec java $JAVA_OPTS -cp "$PMF_ROOT/build/classes:$REPO_ROOT/build/classes" \
  reference.pmf.ReferenceSortingPmfCsvGenerator "$@"