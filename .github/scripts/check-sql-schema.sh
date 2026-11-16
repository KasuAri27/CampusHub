#!/usr/bin/env bash
# 数据库脚本自洽性检查：全新建库与“旧库逐个升级”两条路径得到的结构必须完全一致。
#
# CI 和本机运行同一份脚本，只是 MySQL 客户端的调用方式不同，由 MYSQL_CMD 传入，例如：
#   CI  ：MYSQL_CMD='docker exec -i <容器> mysql --default-character-set=utf8mb4 -uroot -p<密码>'
#   本机：MYSQL_CMD='mysql -uroot -h127.0.0.1 -P3307 --default-character-set=utf8mb4'
# 客户端必须显式使用 utf8mb4，否则中文被误读，按显示宽度限长的约束会误判。
#
# 脚本自己发现要执行的文件，新增升级脚本或建表脚本都不需要改这里：
#   升级脚本：backend/SQL/migrations/*.sql 按文件名排序，每个连续执行两次（验证可重复执行）。
#   建表脚本：backend/SQL/02 至 09 开头的脚本按文件名排序（01 只负责建库，不执行）。
# 数据断言的期望值在 backend/SQL/ci-assertions.env，随 SQL 内容一起维护。
#
# 会删除并重建下面两个库，库名固定以 _ci 结尾，只应指向一次性的校验实例。
set -euo pipefail

: "${MYSQL_CMD:?需要设置 MYSQL_CMD（mysql 客户端的完整调用命令）}"
cd "$(dirname "$0")/../.."

migrated_db=campushub_migration_ci
fresh_db=campushub_schema_ci
resources=backend/src/test/resources

# MYSQL_CMD 是带参数的整条命令，这里有意不加引号让它按空格拆开。
db() { $MYSQL_CMD "$@"; }
strip_use() { sed '/^USE campushub;/d' "$1"; }

# shellcheck disable=SC1091
source backend/SQL/ci-assertions.env

db -e "DROP DATABASE IF EXISTS $migrated_db; DROP DATABASE IF EXISTS $fresh_db;
       CREATE DATABASE $migrated_db CHARACTER SET utf8mb4; CREATE DATABASE $fresh_db CHARACTER SET utf8mb4;"

echo "== 旧库：旧结构 + 旧数据，再逐个执行升级脚本（每个两次）"
strip_use "$resources/legacy-schema.sql" | db "$migrated_db"
db "$migrated_db" < "$resources/legacy-migration-fixture.sql"
for migration in $(ls backend/SQL/migrations/*.sql | sort); do
  echo "   $(basename "$migration")"
  db "$migrated_db" < "$migration" > /dev/null
  db "$migrated_db" < "$migration" > /dev/null
done
actual=$(db -N -B "$migrated_db" -e "SELECT CONCAT(status,':',IF(amount_due IS NULL,'legacy','charged'),':',payment_code) FROM orders WHERE id=1")
test "$actual" = "$LEGACY_ORDER_1" || { echo "旧库订单 1 升级结果不符：期望 $LEGACY_ORDER_1，实际 $actual"; exit 1; }
copied=$(db -N -B "$migrated_db" -e 'SELECT COUNT(*) FROM t_task_flea WHERE task_id=2 AND item_price=12.34')
test "$copied" = "$LEGACY_FLEA_COPIED" || { echo "旧库二手资料复制条数不符：期望 $LEGACY_FLEA_COPIED，实际 $copied"; exit 1; }

echo "== 新库：按编号执行全部建表脚本与测试数据"
for script in $(ls backend/SQL/0[2-9]-*.sql | sort); do
  echo "   $(basename "$script")"
  strip_use "$script" | db "$fresh_db"
done
seeded=$(db -N -B "$fresh_db" -e "SELECT CONCAT((SELECT COUNT(*) FROM users),':',(SELECT COUNT(*) FROM tasks),':',(SELECT COUNT(*) FROM orders),':',(SELECT credit_score FROM users WHERE student_id='241880903'))")
test "$seeded" = "$FRESH_SEED" || { echo "测试数据不符：期望 $FRESH_SEED，实际 $seeded"; exit 1; }

echo "== 结构比对：升级后的旧库必须与全新建库一致（历史保留列除外）"
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT
db -N -B "$fresh_db" < "$resources/schema-fingerprint.sql" > "$workdir/fresh-schema.txt"
db -N -B "$migrated_db" < "$resources/schema-fingerprint.sql" > "$workdir/migrated-schema.txt"
diff -u "$workdir/fresh-schema.txt" "$workdir/migrated-schema.txt"
echo "结构一致，共 $(wc -l < "$workdir/fresh-schema.txt") 项"
