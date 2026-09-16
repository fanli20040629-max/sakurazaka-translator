#!/usr/bin/env bash
# Same source/test manifests as the Windows script. Requires JDK 17 or newer.
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_root"
if [[ -n "${JAVA_HOME:-}" ]]; then
    javac_bin="$JAVA_HOME/bin/javac"
    java_bin="$JAVA_HOME/bin/java"
else
    javac_bin=javac
    java_bin=java
fi
mkdir -p .verification
output_dir="$(mktemp -d "$project_root/.verification/logic.XXXXXX")"
sources=()
while IFS= read -r source_path || [[ -n "$source_path" ]]; do
    source_path="${source_path%$'\r'}"
    [[ -z "$source_path" ]] || sources+=("$source_path")
done < tools/logic-sources.txt
"$javac_bin" --release 17 -encoding UTF-8 -Xlint:all -d "$output_dir" "${sources[@]}"
while IFS= read -r test_class || [[ -n "$test_class" ]]; do
    test_class="${test_class%$'\r'}"
    [[ -z "$test_class" ]] || "$java_bin" -Dfile.encoding=UTF-8 -cp "$output_dir" "$test_class"
done < tools/logic-test-classes.txt
echo 'All desktop logic tests passed. Android build and device tests are separate.'
