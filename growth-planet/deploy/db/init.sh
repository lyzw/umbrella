#!/usr/bin/env bash
#
# 成长星球 · 数据库自建初始化脚本（面向「全新空库」一次性灌库）
# ----------------------------------------------------------------------------
# 两种用法：
#
#  A) 容器化自建（推荐）：直接用同目录 docker-compose.yml
#       cd deploy/db && docker compose up -d
#     MySQL 会在首次启动（空数据卷）时按文件名顺序自动执行 ./sql/*.sql。
#
#  B) 已有 MySQL（自托管 / 云主机）：本脚本把 ./sql 下脚本有序灌入目标库。
#       export DB_PASSWORD=你的root密码
#       ./init.sh
#     或显式传参： ./init.sh <host> <port> <db> <user> <password>
#
# 执行顺序（已与 application-test.yml 的 schema-locations 对齐）：
#   01_schema.sql   = 汇总建表脚本（sprint1~4 + v002~v013 全部，含勋章种子）
#   02_admin.sql    = 运营后台独立账号体系 4 张表（v008，使用 DROP TABLE IF EXISTS，可重复执行）
#
# 说明：
#   - 脚本面向「全新空库」。v008 自带幂等（DROP IF EXISTS），但 01 内的 ALTER/建表非幂等；
#     通过 db_migrations 记录已执行文件避免重复灌入；中途失败需重建库再跑。
#   - 本脚本不负责「存量库升级」；升级请改用对应增量迁移文件（v009~v013）手动按顺序执行。
#   - ./sql 为发布快照，来源于 sk-growthplanet-start/sql/（截至发布日）。
#     后续新增迁移后，请同步更新 ./sql 下文件与执行顺序。
# ----------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="${SCRIPT_DIR}/sql"

# ---- 连接参数：环境变量优先，其次命令行参数，最后默认值 ----
DB_HOST="${DB_HOST:-${1:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${2:-3306}}"
DB_NAME="${DB_NAME:-${3:-growth_planet}}"
DB_USER="${DB_USER:-${4:-root}}"
DB_PASSWORD="${DB_PASSWORD:-${5:-}}"

# 未提供密码则交互式输入（不回显）
if [[ -z "${DB_PASSWORD}" ]]; then
  IFS= read -rs -p "请输入 MySQL ${DB_USER}@${DB_HOST}:${DB_PORT} 的密码: " DB_PASSWORD
  echo
fi

# ---- 检查 mysql 客户端 ----
if ! command -v mysql >/dev/null 2>&1; then
  echo "✗ 未找到 mysql 客户端，请先安装 MySQL client（或改用 docker-compose 方式）。" >&2
  exit 1
fi

# 拼装连接参数（空密码时省略 -p，避免把空串误当密码）
MYSQL_ARGS=( -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" --default-character-set=utf8mb4 )
if [[ -n "${DB_PASSWORD}" ]]; then MYSQL_ARGS+=( -p"${DB_PASSWORD}" ); fi
MYSQL=( mysql "${MYSQL_ARGS[@]}" )

echo "→ 连接 MySQL ${DB_USER}@${DB_HOST}:${DB_PORT} ..."

# 先连管理库，确保目标库存在（utf8mb4 / utf8mb4_0900_ai_ci）
if ! "${MYSQL[@]}" --database=mysql -e \
     "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" ; then
  echo "✗ 无法连接或创建数据库，请检查连接参数 / 网络 / 权限。" >&2
  exit 1
fi
echo "→ 使用数据库 ${DB_NAME}"

# 迁移记录表（幂等保障）
"${MYSQL[@]}" --database="${DB_NAME}" -e \
  "CREATE TABLE IF NOT EXISTS db_migrations (
     id          INT          PRIMARY KEY AUTO_INCREMENT,
     name        VARCHAR(255) NOT NULL,
     applied_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
     UNIQUE KEY  uk_name (name)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"

# 收集 ./sql 下所有 .sql，按文件名排序后执行
shopt -s nullglob
raw_files=( "${SQL_DIR}"/*.sql )
shopt -u nullglob
if [[ ${#raw_files[@]} -eq 0 ]]; then
  echo "✗ 在 ${SQL_DIR} 未找到任何 .sql 文件。" >&2
  exit 1
fi
IFS=$'\n' files=($(printf '%s\n' "${raw_files[@]}" | sort))

for f in "${files[@]}"; do
  name="$(basename "${f}")"
  already=$("${MYSQL[@]}" --database="${DB_NAME}" -N -e \
    "SELECT 1 FROM db_migrations WHERE name='${name}' LIMIT 1;" 2>/dev/null || true)
  if [[ "${already}" == "1" ]]; then
    echo "• 已应用，跳过: ${name}"
    continue
  fi
  echo "→ 应用: ${name}"
  if "${MYSQL[@]}" --database="${DB_NAME}" < "${f}"; then
    "${MYSQL[@]}" --database="${DB_NAME}" -e \
      "INSERT INTO db_migrations(name) VALUES ('${name}');" >/dev/null
    echo "  ✓ ${name} 完成"
  else
    echo "✗ 应用 ${name} 失败，已停止。请排查后重建库或手动修复。" >&2
    exit 1
  fi
done

echo "✔ 数据库初始化完成：${DB_NAME} @ ${DB_HOST}:${DB_PORT}"
echo "  应用侧请将数据源指向该实例（默认 host=127.0.0.1:3306，user=root，库=growth_planet）。"
