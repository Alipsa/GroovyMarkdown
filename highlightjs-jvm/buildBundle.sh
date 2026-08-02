#!/usr/bin/env bash
#
# Regenerates src/main/resources/highlightJs/highlight.js for Rhino.
#
# Requires Node 18+. NOT part of the Maven build - run it by hand when the
# Highlight.js distribution changes, then commit the regenerated bundle.
#
#   ./buildBundle.sh            regenerate the bundle
#   ./buildBundle.sh --verify   regenerate to a temp file and diff against the
#                               committed one; non-zero exit means it is stale
#
# Rhino natively provides Symbol, Map, Set and Object.assign, so this is a
# syntax-only transpile with no polyfills and no hand-editing of any kind.
# The toolchain is installed with `npm ci` from the committed lockfile, so two
# runs produce byte-identical output.
set -euo pipefail

cd "$(dirname "$0")"
SRC="source/highlightJs"
BUNDLE="src/main/resources/highlightJs/highlight.js"

if [ ! -d "$SRC/languages" ]; then
  echo "Missing $SRC - the Highlight.js cdn-assets tree must be present" >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "Node 18+ is required to regenerate the bundle; none found on PATH" >&2
  exit 1
fi
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
if [ "$NODE_MAJOR" -lt 18 ]; then
  echo "Node 18+ is required to regenerate the bundle; found $(node --version)" >&2
  exit 1
fi

if [ -f package-lock.json ]; then
  npm ci --no-audit --no-fund --silent
else
  echo "No package-lock.json yet - bootstrapping one. Commit it alongside the bundle." >&2
  npm install --no-audit --no-fund --silent
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

{
  cat src/main/js/prelude.js
  echo ";"
  cat "$SRC/highlight.min.js"
  echo ";"
  for grammar in $(LC_ALL=C ls "$SRC"/languages/*.min.js | LC_ALL=C sort); do
    cat "$grammar"
    echo ";"
  done
  cat src/main/js/wrapper.js
} > "$WORK/raw.js"

./node_modules/.bin/babel --config-file ./babel.config.json \
  --compact true --no-comments "$WORK/raw.js" --out-file "$WORK/bundle.js"

if [ "${1:-}" = "--verify" ]; then
  if cmp -s "$WORK/bundle.js" "$BUNDLE"; then
    echo "OK: $BUNDLE matches a fresh build"
    exit 0
  fi
  echo "STALE: $BUNDLE differs from a fresh build. Run ./buildBundle.sh and commit." >&2
  exit 1
fi

mkdir -p "$(dirname "$BUNDLE")"
cp "$WORK/bundle.js" "$BUNDLE"
echo "Wrote $BUNDLE ($(wc -c < "$BUNDLE") bytes)"
