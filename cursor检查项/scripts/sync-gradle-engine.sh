#!/usr/bin/env bash
# 将 cursorTaskRules.gradle 复制到目标工程的 lwj_work_by_gradle 目录
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "用法: $0 <项目根目录绝对路径> [gradle相对路径]"
  echo "默认相对路径: app/lwj_work_by_gradle/qd_work_tasks/gradle/cursorTaskRules.gradle"
  exit 1
fi

TARGET_ROOT="$(cd "$1" && pwd)"
REL_PATH="${2:-app/lwj_work_by_gradle/qd_work_tasks/gradle/cursorTaskRules.gradle}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SKILL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_GRADLE="$SKILL_ROOT/gradle/cursorTaskRules.gradle"
DEST_FILE="$TARGET_ROOT/$REL_PATH"

if [[ ! -f "$SRC_GRADLE" ]]; then
  echo "错误: 未找到 skill gradle 引擎: $SRC_GRADLE"
  exit 1
fi

mkdir -p "$(dirname "$DEST_FILE")"
cp -f "$SRC_GRADLE" "$DEST_FILE"
echo "已复制 cursorTaskRules.gradle -> $DEST_FILE"
