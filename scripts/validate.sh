#!/usr/bin/env bash
# Validate Copy Paster sources (compile-time).
#
# What this checks:
#   - ./gradlew compileJava — javac over all main Java sources (types, syntax, etc.)
#
# What this does NOT check:
#   - Eclipse JDT "null type safety" / @NonNull warnings in the IDE (redhat.java).
#     Those come from Minecraft's annotated stubs + generic wildcard rules; javac does
#     not run that analysis. Fix those in source (see net/*Payload.java) or in the IDE.
#
# Usage: ./scripts/validate.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Gradle compile (javac, all main sources)"
./gradlew clean compileJava --warning-mode all

echo "==> OK: compile passed (IDE null-analysis is separate; see script header)"
