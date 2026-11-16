#!/usr/bin/env bash
# 判断本次改动落在哪些范围，供后续任务决定是否真正执行检查。
#
# 输入（环境变量）：EVENT_NAME、REF_NAME、BASE_SHA、HEAD_SHA。
# 输出：backend / frontend / sql / entity_schema 四个 true|false，写入 $GITHUB_OUTPUT；本机运行时直接打印。
#
# 保守原则：宁可多跑不可漏跑。手动触发、推送到 main、拿不到可靠的对比基准，
# 或改动了 .github/ 下任何文件（流水线自身变了）时，四个范围全部算命中。
set -euo pipefail

out="${GITHUB_OUTPUT:-/dev/stdout}"
scopes="backend frontend sql entity_schema"

run_all() {
  echo "全部检查都执行：$1" >&2
  for scope in $scopes; do echo "$scope=true" >> "$out"; done
  exit 0
}

[ "${EVENT_NAME:-}" = "workflow_dispatch" ] && run_all "手动触发"
[ "${EVENT_NAME:-}" = "push" ] && [ "${REF_NAME:-}" = "main" ] && run_all "推送到 main"

base="${BASE_SHA:-}"
head="${HEAD_SHA:-HEAD}"
# 新建分支的首次推送没有上一个提交（全 0）；强推后旧提交也可能已不可达。
if [ -z "$base" ] || [[ "$base" =~ ^0+$ ]] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
  run_all "没有可用的对比基准"
fi

# PR 用三点对比：只看本分支相对合并基的改动，不把 main 上别人的新提交算进来。
# 推送用两点对比：本次推送前后的差异。
if [ "${EVENT_NAME:-}" = "pull_request" ]; then range="$base...$head"; else range="$base..$head"; fi
# quotepath=false：否则含中文的路径会被加引号转义，行首多一个引号，下面的前缀匹配会漏掉。
files=$(git -c core.quotepath=false diff --name-only "$range")

if grep -q '^\.github/' <<<"$files"; then run_all "流水线自身有改动"; fi

hit() { if grep -Eq "$1" <<<"$files"; then echo true; else echo false; fi; }

{
  # 后端业务测试：后端目录下的改动，但不含 backend/SQL/。
  # 业务测试用的库由实体直接生成，不读建表脚本，脚本单独改动时不必连带跑业务测试。
  if grep -E '^backend/' <<<"$files" | grep -Evq '^backend/SQL/'; then echo "backend=true"; else echo "backend=false"; fi
  echo "frontend=$(hit '^frontend/')"
  # 建库与升级脚本互相比对：SQL 脚本，或比对用的旧库结构、旧库数据、指纹查询。
  echo "sql=$(hit '^backend/SQL/|^backend/src/test/resources/(legacy-schema|legacy-migration-fixture|schema-fingerprint)\.sql$')"
  # 实体与建表脚本是否一致：任意一边变了都要查，依赖升级（pom）也可能改变映射行为。
  echo "entity_schema=$(hit '^backend/SQL/|^backend/src/main/|^backend/pom\.xml$')"
} >> "$out"
