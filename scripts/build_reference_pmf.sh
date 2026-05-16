#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PMF_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(CDPATH= cd -- "$PMF_ROOT/../.." && pwd)

mkdir -p "$REPO_ROOT/build/classes"
find "$REPO_ROOT/src" -name '*.java' -print0 |
  xargs -0 javac -encoding UTF-8 -d "$REPO_ROOT/build/classes"

mkdir -p "$PMF_ROOT/build/classes"
find "$PMF_ROOT/src" -name '*.java' -print0 |
  xargs -0 javac -encoding UTF-8 -cp "$REPO_ROOT/build/classes" -d "$PMF_ROOT/build/classes"

printf 'Compiled main classes to %s\n' "$REPO_ROOT/build/classes"
printf 'Compiled reference PMF classes to %s\n' "$PMF_ROOT/build/classes"