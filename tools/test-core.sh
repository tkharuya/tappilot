#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
classes_dir="$project_dir/build/core-tests"
mkdir -p "$classes_dir"
sources=("$project_dir/app/src/main/java/jp/tappilot/app/Plan.java" "$project_dir/app/src/main/java/jp/tappilot/app/Runner.java" "$project_dir/tests/RunnerTest.java")
if [ -n "${TAPPILOT_ECJ_JAR:-}" ]; then
    java -jar "$TAPPILOT_ECJ_JAR" -17 -proc:none -encoding UTF-8 -d "$classes_dir" "${sources[@]}"
else
    javac -encoding UTF-8 -d "$classes_dir" "${sources[@]}"
fi
java -cp "$classes_dir" jp.tappilot.app.RunnerTest
