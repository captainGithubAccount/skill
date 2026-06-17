#!/usr/bin/env bash
# 将 skill 内 .cursor 目录同步到目标 Android 工程根目录（合并 rules，不删除目标工程其它 .cursor 内容）
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "用法: $0 <项目根目录绝对路径>"
  echo "示例: $0 /Users/xxx/my-android-project"
  exit 1
fi

TARGET_ROOT="$(cd "$1" && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SKILL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_CURSOR="$SKILL_ROOT/.cursor"

if [[ ! -d "$SRC_CURSOR/rules" ]]; then
  echo "错误: skill 内未找到 .cursor/rules: $SRC_CURSOR/rules"
  exit 1
fi

mkdir -p "$TARGET_ROOT/.cursor/rules" "$TARGET_ROOT/.cursor/buildTxt"

# 合并复制 task- 规则（覆盖同名，保留目标工程其它 rules）
cp -f "$SRC_CURSOR/rules/"*.mdc "$TARGET_ROOT/.cursor/rules/" 2>/dev/null || true

# 确保 buildTxt 目录存在（运行 cursorCodeReviewCheck 后才会生成 txt）
touch "$TARGET_ROOT/.cursor/buildTxt/.gitkeep"

echo "已同步 .cursor 到: $TARGET_ROOT/.cursor"
echo "  rules: $(ls -1 "$TARGET_ROOT/.cursor/rules"/*.mdc 2>/dev/null | wc -l | tr -d ' ') 个 .mdc"
