#!/usr/bin/env bash
# Type-checks and tests the ai-assist Edge Function logic under Node, with a mocked network.
# The Deno entry point (index.ts) is not run here; Deno is not installed in this environment.
#
#   backend/scripts/test-functions.sh
#
# Needs tsc and Node 18+ on the PATH. Nothing is installed from the network.
set -euo pipefail

BACKEND="$(cd "$(dirname "$0")/.." && pwd)"
FN="$BACKEND/supabase/functions/ai-assist"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

command -v tsc >/dev/null || { echo "tsc not found on PATH" >&2; exit 2; }

echo "== type-checking handler.ts (strict)"
tsc --strict --noEmit --target es2022 --module es2022 --moduleResolution bundler \
  --lib es2022,dom "$FN/handler.ts"

echo "== compiling handler.ts for tests"
mkdir -p "$OUT/build" "$OUT/test"
tsc --strict --target es2022 --module es2022 --moduleResolution bundler \
  --lib es2022,dom --outDir "$OUT/build" "$FN/handler.ts"
echo '{"type":"module"}' > "$OUT/build/package.json"
cp "$FN/test/handler.test.mjs" "$OUT/test/"

echo "== running handler tests"
node --test "$OUT/test/handler.test.mjs"
