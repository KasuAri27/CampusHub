#!/usr/bin/env bash
# 实体与建表脚本一致性检查：用建表脚本建出的库，后端必须能以 ddl-auto=validate 启动。
#
# 为什么需要：业务测试用的库是 Hibernate 按实体直接生成的，建表脚本只和升级脚本互相比对，
# 两边都不会发现“实体改了、脚本没跟上”（或反过来）。本检查把两者接到一起。
# 能发现：实体映射的表或列在脚本里不存在、列类型不兼容。
# 发现不了：脚本里多出的列、非空与长度差异（validate 不比较这些）。
#
# 输入（环境变量）：
#   MYSQL_CMD   mysql 客户端的完整调用命令（同 check-sql-schema.sh）
#   JDBC_URL    指向校验库 campushub_validate_ci 的 JDBC 地址
#   DB_USER / DB_PASSWORD   连接校验库的账号
#   APP_JAR     已打好的后端可执行包；不设则取 backend/target 下的 campushub-*.jar
#   JAVA_CMD    可选，默认 java（需要 JDK 17）
#   APP_PORT    可选，默认 18080
#
# 会删除并重建 campushub_validate_ci，只应指向一次性的校验实例。
set -euo pipefail

: "${MYSQL_CMD:?需要设置 MYSQL_CMD（mysql 客户端的完整调用命令）}"
: "${JDBC_URL:?需要设置 JDBC_URL（指向 campushub_validate_ci）}"
: "${DB_USER:?需要设置 DB_USER}"
cd "$(dirname "$0")/../.."

validate_db=campushub_validate_ci
java_cmd="${JAVA_CMD:-java}"
port="${APP_PORT:-18080}"
jar="${APP_JAR:-$(ls backend/target/campushub-*.jar 2>/dev/null | head -1)}"
[ -n "$jar" ] && [ -f "$jar" ] || { echo "找不到后端可执行包，请先在 backend 下执行 mvn -DskipTests package"; exit 1; }
case "$JDBC_URL" in *"$validate_db"*) ;; *) echo "JDBC_URL 必须指向 $validate_db"; exit 1 ;; esac

db() { $MYSQL_CMD "$@"; }
strip_use() { sed '/^USE campushub;/d' "$1"; }

echo "== 用建表脚本建库 $validate_db"
db -e "DROP DATABASE IF EXISTS $validate_db; CREATE DATABASE $validate_db CHARACTER SET utf8mb4;"
for script in $(ls backend/SQL/0[2-9]-*.sql | sort); do
  strip_use "$script" | db "$validate_db"
done

echo "== 以 ddl-auto=validate 启动后端"
log=$(mktemp)
# 密钥是只用于本检查的固定占位值，不对应任何真实环境；关闭定时任务，避免启动后改动校验库。
CAMPUSHUB_JWT_SECRET=schema-check-only-0123456789abcdef0123456789abcdef0123456789abcdef \
CAMPUSHUB_AES_KEY=schema-check-only-0123456789abcd \
CAMPUSHUB_SCHEDULING_ENABLED=false \
"$java_cmd" -jar "$jar" \
  --server.port="$port" \
  --spring.datasource.url="$JDBC_URL" \
  --spring.datasource.username="$DB_USER" \
  --spring.datasource.password="${DB_PASSWORD:-}" \
  --spring.jpa.hibernate.ddl-auto=validate \
  --spring.jpa.show-sql=false \
  --logging.level.com.campushub=INFO > "$log" 2>&1 &
app_pid=$!
trap 'kill "$app_pid" 2>/dev/null || true; rm -f "$log"' EXIT

# 最多等 120 秒：出现启动完成的日志即通过；进程提前退出即失败。
for _ in $(seq 1 120); do
  if grep -q 'Started .*Application in' "$log"; then
    echo "通过：实体与建表脚本一致，后端在校验模式下启动成功"
    exit 0
  fi
  if ! kill -0 "$app_pid" 2>/dev/null; then
    echo "失败：后端在校验模式下无法启动，实体与建表脚本不一致或启动配置有误"
    # 先单独列出结构校验给出的原因（缺表、缺列、列类型不符），它在日志里埋得很深。
    grep -o 'Schema-validation: [^;]*' "$log" | sort -u | sed 's/^/原因：/' || true
    echo "---- 日志末尾 ----"
    tail -30 "$log"
    exit 1
  fi
  sleep 1
done
echo "失败：120 秒内没有启动完成"
tail -30 "$log"
exit 1
