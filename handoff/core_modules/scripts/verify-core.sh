#!/usr/bin/env bash
# 从任意工作目录运行；产物只写进本次临时目录，不污染源码或旧构建目录。
set -euo pipefail
module_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="${1:-$module_dir/src}"
if [[ ! -d "$source_dir" ]]; then printf '源码目录不存在：%s\n' "$source_dir" >&2; exit 2; fi
java_cmd=java
javac_cmd=javac
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_cmd="$JAVA_HOME/bin/java"
  javac_cmd="$JAVA_HOME/bin/javac"
fi
"$javac_cmd" -version
build_dir="$(mktemp -d "${TMPDIR:-/tmp}/sakura-core.XXXXXX")"
sources=()
while IFS= read -r -d '' source; do sources+=("$source"); done < <(find "$source_dir" "$module_dir/tools" -name '*.java' -type f -print0)
"$javac_cmd" --release 17 -encoding UTF-8 -Xlint:all -Werror -d "$build_dir" "${sources[@]}"
"$java_cmd" -cp "$build_dir" CoreModulesSelfTest
printf '测试编译产物：%s\n' "$build_dir"
