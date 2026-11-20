#!/usr/bin/env bash
# Compiles and runs the JVM checks that need no Android SDK:
#   - shared/ core (sessions, entitlements, AI rules, sync rules)
#   - the pure allowlist decision in app/.../AllowRules.kt
#
# Requires a Kotlin 2.x compiler. Point KOTLIN_LIB_DIR at a directory holding
# kotlin-compiler-embeddable, kotlin-stdlib, kotlin-script-runtime, kotlin-reflect,
# kotlin-daemon-embeddable, trove4j, kotlinx-coroutines-core-jvm and annotations jars
# (for example the lib/ folder of a Gradle 8.x install).
#
#   KOTLIN_LIB_DIR=/opt/gradle-8.14.3/lib scripts/verify-kotlin.sh
#
# Exits non-zero on any compile error, warning (warnings are errors) or failed check.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIB="${KOTLIN_LIB_DIR:?set KOTLIN_LIB_DIR to a folder containing Kotlin 2.x compiler jars}"

jars() {
  local files=()
  for pattern in kotlin-compiler-embeddable kotlin-stdlib kotlin-script-runtime kotlin-reflect \
                 kotlin-daemon-embeddable trove4j kotlinx-coroutines-core-jvm annotations; do
    local match
    match=$(ls "$LIB"/"$pattern"-*.jar 2>/dev/null | head -n 1 || true)
    if [ -z "$match" ]; then
      echo "missing jar: $pattern in $LIB" >&2
      exit 2
    fi
    files+=("$match")
  done
  printf '%s\n' "${files[@]}"
}

COMPILER_CP=$(jars | paste -sd: -)
STDLIB=$(ls "$LIB"/kotlin-stdlib-*.jar | head -n 1)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

compile() {
  java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -cp "$STDLIB" -jvm-target 17 -Werror -d "$OUT" "$@"
}

echo "== compiling shared core and allowlist rules"
compile $(find "$ROOT/shared/src/main" -name '*.kt') \
  "$ROOT/shared/src/test/kotlin/app/lockedin/core/CoreChecks.kt" \
  "$ROOT/shared/src/test/kotlin/app/lockedin/core/AiWireChecks.kt" \
  "$ROOT/shared/src/test/kotlin/app/lockedin/core/SpecVectorChecks.kt" \
  "$ROOT/shared/src/test/kotlin/app/lockedin/core/AuthWireChecks.kt" \
  "$ROOT/shared/src/test/kotlin/app/lockedin/core/SessionChecks.kt" \
  "$ROOT/shared/src/test/kotlin/app/lockedin/core/BackendSettingsChecks.kt" \
  "$ROOT/app/src/main/java/app/lockedin/phone/AllowRules.kt" \
  "$ROOT/app/src/test/kotlin/app/lockedin/phone/AllowRulesChecks.kt"

echo "== running shared core checks"
java -cp "$OUT:$STDLIB" app.lockedin.core.CoreChecksKt

echo "== running AI wire checks"
java -cp "$OUT:$STDLIB" app.lockedin.core.AiWireChecksKt

echo "== running cross-platform spec vectors (Kotlin reference)"
java -cp "$OUT:$STDLIB" app.lockedin.core.SpecVectorChecksKt "$ROOT/shared/spec/vectors.json"

echo "== running auth wire checks"
java -cp "$OUT:$STDLIB" app.lockedin.core.AuthWireChecksKt

echo "== running session lifecycle checks"
java -cp "$OUT:$STDLIB" app.lockedin.core.SessionChecksKt

echo "== running backend settings checks"
java -cp "$OUT:$STDLIB" app.lockedin.core.BackendSettingsChecksKt

echo "== running allowlist checks"
java -cp "$OUT:$STDLIB" app.lockedin.phone.AllowRulesChecksKt
