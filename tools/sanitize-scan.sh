#!/usr/bin/env bash
# 脱敏扫描 —— 提交前 / 推送前 / 发布前必跑
#
# 用法：
#   tools/sanitize-scan.sh              # 扫当前工作树（提交前用这个）
#   tools/sanitize-scan.sh --staged     # 只扫已暂存内容（pre-commit 钩子用）
#   tools/sanitize-scan.sh --all        # 扫全部 ref 的树 + 全部历史（发布前用）
#
# 退出码：0 = 干净；1 = 有命中
#
# 为什么需要这个脚本：
#   旧规范只扫 `git diff <base>..HEAD`，扫不到历史。历史一旦推上去就是公开的，
#   改当前文件没用。2026-10-31 的审计靠手动跑 `git log --all -p` 才查出
#   5 处本机路径和 46 处真实显示名。
#
# 设计要点：
#   · 用 git pathspec 排除第三方目录，而不是逐行 grep 排除（历史扫描拿不到路径）
#   · 结果写临时文件，避免命令替换吞掉二进制文件的 null 字节
#   · 工作树与历史分开报告：工作树必须干净，历史已推送的只能记录

set -uo pipefail

MODE="${1:-default}"
FAIL=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ── 排除的路径（第三方 / 上游 / 生成物）──────────────────────────────
# 这些目录里的 IP、密钥、路径都是上游自带的，不是我们的
PATHSPEC=(
  ':(exclude)TMessagesProj/jni'
  ':(exclude)TMessagesProj_Modules'
  ':(exclude)design-skills'
  ':(exclude)*.min.js'
  ':(exclude)*.lock'
  ':(exclude)gradle/wrapper'
  ':(exclude)tools/sanitize-scan.sh'
)

# ── 规则：<名字>|<PCRE>────────────────────────────────────────────────
RULES=(
  '私人邮箱|2602280862@qq\.com'
  '飞书 ID|(?:oc|om)_[0-9a-f]{16,}'
  '本机绝对路径|/home/blancat/'
  '本机用户名|blancat(?!ora)'
  '真实显示名|BlanCat(?!Ora|ora)'
  '内网 IP|192\.168\.[0-9]+\.[0-9]+'
  '私钥|BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY'
  'OpenAI 风格密钥|(?<![A-Za-z0-9])sk-[A-Za-z0-9_-]{20,}'
  'GitHub Token|gh[pousr]_[A-Za-z0-9]{20,}'
  'AWS Key|AKIA[0-9A-Z]{16}'
)

# ── 已知误报（逐条确认过，不是泄漏）──────────────────────────────────
# Telegram 上游公开测试凭据：与 origin/master 逐字节相同
ALLOW='AIzaSyA-t0jLPjUt2FxrA8VPK2EiYHcYcboIR6k|AIzaSyDqt8P-7F7CPCseMkOiVRgb1LY8RN1bvH8'
# Dockerfile 的构建用户、sqlite 文档示例
ALLOW+='|/home/gradle|/home/source|/home/fred'
# webrtc 测试固定 IP
ALLOW+='|192\.168\.(?:0\.[0-3]|1\.[125]|2\.1|7\.1|11\.11|12\.1|30\.208|111\.222)'
# 文档占位符
ALLOW+='|/home/<|/home/user|/home/example'
# 扫描器自己的规则定义行（形如 '名字|正则'）
ALLOW+="|^\\+  '.*\\|.*'$"

scan() {
  local label="$1" src="$2"
  for rule in "${RULES[@]}"; do
    local name="${rule%%|*}" pat="${rule#*|}"
    local hits
    hits=$(grep -aP "$pat" "$src" 2>/dev/null | grep -vP "$ALLOW" | head -5)
    if [ -n "$hits" ]; then
      echo "  ❌ [$name] $label"
      printf '%s\n' "$hits" | cut -c1-200 | sed 's/^/       /'
      FAIL=1
    fi
  done
}

echo "═══════════════════════════════════════════════════════════"
echo " 脱敏扫描  mode=$MODE  $(date '+%Y-%m-%d %H:%M:%S')"
echo "═══════════════════════════════════════════════════════════"

case "$MODE" in
  --staged)
    echo "▸ 已暂存内容（只看新增行）"
    git diff --cached -U0 > "$TMP/staged.raw" 2>/dev/null
    # 只看新增行：删除行是修复本身，不是泄漏
    grep -aE '^\+' "$TMP/staged.raw" | grep -avE '^\+\+\+' > "$TMP/staged.txt" 2>/dev/null || : > "$TMP/staged.txt"
    scan "staged" "$TMP/staged.txt"
    ;;

  --all)
    echo "▸ 当前工作树"
    git grep -nIP '' -- . "${PATHSPEC[@]}" > "$TMP/wt.txt" 2>/dev/null
    scan "worktree" "$TMP/wt.txt"

    echo "▸ 全部 ref 的树"
    for ref in $(git for-each-ref --format='%(refname:short)' refs/heads refs/remotes 2>/dev/null); do
      git grep -nIP '' "$ref" -- . "${PATHSPEC[@]}" > "$TMP/tree.txt" 2>/dev/null
      scan "tree:$ref" "$TMP/tree.txt"
    done

    echo "▸ 全部历史（可能较慢）"
    git log --all -p -- . "${PATHSPEC[@]}" > "$TMP/hist.raw" 2>/dev/null
    # 只看新增行：历史里被删除的内容从未存在于任何 commit
    grep -aE '^\+' "$TMP/hist.raw" | grep -avE '^\+\+\+' > "$TMP/hist.txt" 2>/dev/null || : > "$TMP/hist.txt"
    scan "history" "$TMP/hist.txt"
    ;;

  *)
    echo "▸ 当前工作树"
    git grep -nIP '' -- . "${PATHSPEC[@]}" > "$TMP/wt.txt" 2>/dev/null
    scan "worktree" "$TMP/wt.txt"

    echo "▸ 本分支历史"
    git log -p -- . "${PATHSPEC[@]}" > "$TMP/hist.raw" 2>/dev/null
    grep -aE '^\+' "$TMP/hist.raw" | grep -avE '^\+\+\+' > "$TMP/hist.txt" 2>/dev/null || : > "$TMP/hist.txt"
    scan "history" "$TMP/hist.txt"
    ;;
esac

echo "───────────────────────────────────────────────────────────"
if [ "$FAIL" -eq 0 ]; then
  echo " ✅ 干净，可以继续"
  exit 0
fi

echo " ❌ 有命中"
echo
echo " 判断标准："
echo "   · worktree 干净 = 当前状态可以推"
echo "   · history 有命中 = 已推送的历史里有，改当前文件消不掉"
echo
echo " 处理原则："
echo "   · 当前文件有命中 → 改成占位符，新提交覆盖"
echo "   · 历史有命中且已推送 → 不重写历史，改当前文件 + 记录"
echo "   · 历史有命中且未推送 → 可以重建干净历史"
exit 1
