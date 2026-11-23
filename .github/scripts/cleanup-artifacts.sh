#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 账号级统一制品清理（第 54 批）
#
# GitHub 原生只支持按天数保留（retention-days），做不到「按类别只留最近 N 个」。
# Actions 存储配额是**账号级共享**（与 Packages 共用），一个仓库堆爆会连累
# 同账号下所有仓库的 upload-artifact：表现为「上传步骤 1 秒失败、前面编译全绿，
# 错误信息完全不提配额」。因此每个仓库都必须在本地收敛自己的制品。
#
# 统一口径（使用者 2026-11-23 拍板）：
#   *debug*        只保留最近 1 个
#   *release*      只保留最近 3 个
#   其余一切       只保留最近 3 个
#
# 用法：
#   cleanup-artifacts.sh                              # 自动分类，推荐
#   cleanup-artifacts.sh --pattern 'foo-*' --keep 5   # 显式规则（兼容旧调用）
#   cleanup-artifacts.sh --dry-run                    # 自动分类 + 预演
#
# 依赖：gh、jq（GitHub 托管 runner 自带）
# 权限：GH_TOKEN 且具备 actions: write
# ---------------------------------------------------------------------------
set -euo pipefail

PATTERN=""; KEEP=""; OLDER_DAYS=0; DRY_RUN=false
REPO="${GITHUB_REPOSITORY:-}"
die() { echo "❌ $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pattern)    PATTERN="$2";    shift 2 ;;
    --keep)       KEEP="$2";       shift 2 ;;
    --older-than) OLDER_DAYS="$2"; shift 2 ;;
    --dry-run)    DRY_RUN=true;    shift   ;;
    --repo)       REPO="$2";       shift 2 ;;
    *)            die "未知参数：$1" ;;
  esac
done

[[ -n "$REPO" ]] || die "缺少 --repo（或设置 GITHUB_REPOSITORY）"
command -v gh >/dev/null || die "找不到 gh"
command -v jq >/dev/null || die "找不到 jq"

# 显式模式 MUST 同时给 pattern 与 keep，否则退回自动分类会误删。
MODE=auto
if [[ -n "$PATTERN" || -n "$KEEP" ]]; then
  [[ -n "$PATTERN" && -n "$KEEP" ]] || die "--pattern 与 --keep 必须同时提供"
  MODE=explicit
fi

mb() { awk -v b="$1" 'BEGIN { printf "%.1f", b/1048576 }'; }

category_of() {
  local n="${1,,}"
  case "$n" in
    *debug*)                         echo debug   ;;
    *release*|*-preview-*|*v*.apk)   echo release ;;
    *)                               echo other   ;;
  esac
}

echo "仓库：$REPO"
if [[ "$MODE" == auto ]]; then
  echo "模式：自动分类（debug 留 1 / release 留 3 / 其它留 3）"
else
  echo "模式：显式规则 $PATTERN 留 $KEEP 个"
fi
echo "仅清理 ${OLDER_DAYS} 天前（0 = 不限）"
[[ "$DRY_RUN" == true ]] && echo "预演：不实际删除"
echo ""

CUTOFF=0
[[ $OLDER_DAYS -gt 0 ]] && CUTOFF=$(date -u -d "-${OLDER_DAYS} days" +%s)

# 按 created_at 倒序：同一类别内也就是「最近 N 个」；ISO 时间字符串可直接字典序排。
mapfile -t ROWS < <(
  gh api --paginate "repos/${REPO}/actions/artifacts" \
     --jq '.artifacts[] | select(.expired==false)
           | "\(.created_at)\t\(.id)\t\(.size_in_bytes)\t\(.name)"' \
  | sort -r
)

declare -A SEEN
matched=0; del_cnt=0; del_bytes=0
while IFS=$'\t' read -r created id size name; do
  [[ -z "${id:-}" ]] && continue

  if [[ "$MODE" == explicit ]]; then
    # shellcheck disable=SC2254
    case "$name" in
      $PATTERN) ;;
      *) continue ;;
    esac
    c=explicit
    limit=$KEEP
  else
    c=$(category_of "$name")
    case "$c" in
      debug)   limit=1 ;;
      release) limit=3 ;;
      *)       limit=3 ;;
    esac
  fi

  matched=$(( matched + 1 ))
  SEEN[$c]=$(( ${SEEN[$c]:-0} + 1 ))
  if [[ ${SEEN[$c]} -le $limit ]]; then
    printf '  [保留/%s] %s  %6.1fMB  %s\n' "$c" "${created:0:16}" "$(mb "$size")" "$name"
    continue
  fi

  if [[ $OLDER_DAYS -gt 0 ]]; then
    ts=$(date -u -d "$created" +%s)
    [[ $ts -gt $CUTOFF ]] && { printf '  [未满期] %s  %s\n' "${created:0:16}" "$name"; continue; }
  fi

  cur_mb=$(mb "$size")
  if [[ "$DRY_RUN" == true ]]; then
    printf '  [可删/%s] %s  %6.1fMB  %s\n' "$c" "${created:0:16}" "$cur_mb" "$name"
  else
    if gh api -X DELETE "repos/${REPO}/actions/artifacts/${id}" >/dev/null 2>&1; then
      printf '  [已删/%s] %s  %6.1fMB  %s\n' "$c" "${created:0:16}" "$cur_mb" "$name"
    else
      printf '  [失败] %s  %s（id=%s）\n' "${created:0:16}" "$name" "$id"
    fi
  fi
  del_cnt=$(( del_cnt + 1 )); del_bytes=$(( del_bytes + size ))
done <<< "$(printf '%s\n' "${ROWS[@]}")"

MB=$(mb "$del_bytes")
echo ""
if [[ "$DRY_RUN" == true ]]; then
  echo "预演结果：命中 $matched 个，可删 $del_cnt 个，可释放 ${MB} MB"
else
  echo "清理完成：命中 $matched 个，删除 $del_cnt 个，释放 ${MB} MB"
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### 制品清理（$MODE）"
    echo ""
    if [[ "$MODE" == auto ]]; then
      echo "- 口径：debug 留 1 / release 留 3 / 其它留 3"
    else
      echo "- 规则：\`$PATTERN\` 留 $KEEP 个"
    fi
    echo "- 命中：$matched 个"
    echo "- $( [[ $DRY_RUN == true ]] && echo 可删 || echo 已删 )：$del_cnt 个"
    echo "- $( [[ $DRY_RUN == true ]] && echo 可释放 || echo 已释放 )：**${MB} MB**"
  } >> "$GITHUB_STEP_SUMMARY"
fi
