#!/usr/bin/env bash
# Compiles a pure-JVM slice of the app and runs its JUnit 4 tests WITHOUT Gradle.
#
#   bash tools/jvm-tests-standalone.sh <test class>...
#
# with MAIN_EXTRA and TEST_EXTRA naming the slice's source files, relative to the package root
# under app/src/main/java and app/src/test/java. Example, Priya's slice:
#
#   MAIN_EXTRA="data/knowledge/KnowledgeFacts.kt ml/llm/LogPrefilter.kt ml/llm/SafetyLine.kt" #   TEST_EXTRA="data/knowledge/KnowledgeFactsTest.kt ml/llm/SafetyLineTest.kt" #   bash tools/jvm-tests-standalone.sh #     io.github.vedant7007.katori.data.knowledge.KnowledgeFactsTest #     io.github.vedant7007.katori.ml.llm.SafetyLineTest
#
# WHY. Six sessions, one Gradle cache, and a Gradle run that takes minutes and serialises on a
# daemon lock. Anything that does not need the Android SDK (domain/, the LLM prompts and guards,
# the food matcher and its bundled database, the knowledge file, the orchestrator against fakes)
# compiles against the Kotlin compiler jars already in ~/.gradle/caches, in about twenty
# seconds, from any worktree, and never touches app/build or the daemon. The root is taken from
# `git rev-parse`, so it runs in a session worktree as-is.
#
# WHAT IT IS NOT. Not the Gradle run and not a test count anyone quotes: no Room, no Hilt, no
# Android classes, and it does not honour the test-input declarations in app/build.gradle.kts.
# It is the fast loop. The integrator's Gradle run, read from its own JUnit XML, is the one that
# counts (COORDINATION.md, 20 September 2026).
#
# Requires: Git Bash (cygpath), a JDK on PATH, and the jars in the Gradle cache that a normal
# Gradle build already downloaded. KOTLIN pins the compiler version; match libs.versions.toml.
set -euo pipefail
ROOT="${ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || echo /c/CODING/IQOOOOO)}"
PKG=io/github/vedant7007/katori
SRC=$ROOT/app/src/main/java/$PKG
TST=$ROOT/app/src/test/java/$PKG
OUT="${OUT:-$ROOT/logs/jvm-tests-standalone-classes}"   # logs/ is gitignored and per worktree
KOTLIN="${KOTLIN:-2.2.20}"   # match libs.versions.toml
C=~/.gradle/caches/modules-2/files-2.1
jar() { ls "$C/$1/$2"/*/*.jar | grep -v sources | head -1; }
w() { cygpath -w "$1"; }
join() { local IFS=';'; echo "$*"; }

STD="$(jar org.jetbrains.kotlin/kotlin-stdlib "$KOTLIN")"
COMPILER_CP=$(join \
  "$(w "$(jar org.jetbrains.kotlin/kotlin-compiler-embeddable "$KOTLIN")")" \
  "$(w "$STD")" \
  "$(w "$(jar org.jetbrains.kotlin/kotlin-script-runtime "$KOTLIN")")" \
  "$(w "$(jar org.jetbrains.kotlin/kotlin-reflect 2.2.0)")" \
  "$(w "$(jar org.jetbrains.kotlin/kotlin-daemon-embeddable "$KOTLIN")")" \
  "$(w "$(jar org.jetbrains.intellij.deps/trove4j 1.0.20200330)")" \
  "$(w "$(jar org.jetbrains/annotations 13.0)")" \
  "$(w "$(jar org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.10.2)")")
JUNIT_CP=$(join "$(w "$(jar junit/junit 4.13.2)")" "$(w "$(jar org.hamcrest/hamcrest-core 1.3)")" "$(w "$(jar org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.10.2)")" "$(w "$(jar org.xerial/sqlite-jdbc 3.53.4.0)")")

# The pure-JVM core every slice tends to need. Add to it with MAIN_EXTRA; do not add Android.
CORE=(
  domain/model/Outcome.kt domain/model/Confidence.kt domain/model/Nutrients.kt
  domain/RangeDirection.kt domain/RulesEngine.kt domain/RuleTemplates.kt domain/DefaultRulesEngine.kt
  ml/llm/LlmEngine.kt ml/llm/Prompts.kt ml/llm/DefaultNumericGuard.kt ml/llm/LlamaRuntime.kt
  ml/llm/ExtractionJson.kt ml/llm/LlamaCppLlmEngine.kt ml/llm/ConversationPrompts.kt
  data/food/FoodTextMatching.kt
)
FILES=()
for f in "${CORE[@]}" ${MAIN_EXTRA:-}; do FILES+=("$(w "$SRC/$f")"); done
for f in ${TEST_EXTRA:-}; do FILES+=("$(w "$TST/$f")"); done
[ $# -gt 0 ] || { echo "usage: $0 <test class>..." >&2; exit 2; }

rm -rf "$OUT"; mkdir -p "$OUT"
echo "== compiling ${#FILES[@]} files with Kotlin $KOTLIN"
java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn \
  -cp "$(w "$STD");$JUNIT_CP" -d "$(w "$OUT")" -jvm-target 17 -Xsuppress-version-warnings "${FILES[@]}"
echo "== running $*"
java -cp "$(w "$OUT");$(w "$STD");$JUNIT_CP" -Dkatori.projectDir="$(w "$ROOT")" -Dfile.encoding=UTF-8 \
  org.junit.runner.JUnitCore "$@"
