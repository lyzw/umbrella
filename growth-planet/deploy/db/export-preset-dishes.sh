#!/usr/bin/env bash
# -------------------------------------------------------------------
# 成长星球 · 预置菜品初始化数据导出脚本
# -------------------------------------------------------------------
# 导出范围（生成可重放的 03_preset_dishes.sql，按外键依赖排序）：
#   1) life_dish_category   全量 —— 预置菜品的外键依赖（category_id），须先导入
#   2) life_dish            全表 —— 这张表本身就是预置菜品（家庭菜品在 life_family_dish，
#                                   独立自增序列，两表裸 id 会撞号，勿混）
#   3) life_dish_ingredient 仅 owner_type='PRESET' —— 配方食材子表与家庭共用，按归属过滤
#
# 说明：
#   * 完整 INSERT 保留原始主键 id（菜单/引用依赖 id 稳定，AUTO_INCREMENT 随之对齐）；
#   * INSERT IGNORE 语义：重复灌入安全（主键冲突自动跳过），外键缺失行也会被静默跳过；
#   * 含软删行（delete_at>0）一并镜像，保证与源库完全一致，应用层自行过滤。
#
# 用法（在能连到目标库的机器上执行，如服务器本机或经 SSH 隧道）：
#   DB_HOST=127.0.0.1 DB_USER=root DB_PASSWORD='xxx' ./export-preset-dishes.sh [输出文件]
# 默认输出: ./03_preset_dishes.sql → 放入 deploy/db/sql/ 作为第 3 号初始化脚本
# （灌库顺序：01_schema.sql → 02_admin.sql → 03_preset_dishes.sql）
# -------------------------------------------------------------------
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:?请先 export DB_PASSWORD=<数据库密码>}"
DB_NAME="${DB_NAME:-growth_planet}"
OUT="${1:-03_preset_dishes.sql}"

command -v mysqldump >/dev/null 2>&1 || { echo "✗ 未找到 mysqldump，请先安装 mysql 客户端" >&2; exit 1; }

# --compact: 精简输出；--no-create-info: 纯数据；--skip-extended-insert: 一行一条 INSERT 便于 diff；
# --insert-ignore: 可重放（主键冲突跳过，不破坏已有数据）
DUMP_OPTS=(
  --compact
  --no-create-info
  --skip-triggers
  --skip-extended-insert
  --insert-ignore
  --skip-set-charset
)

export MYSQL_PWD="${DB_PASSWORD}"

{
  echo "-- ============================================================="
  echo "-- 成长星球 · 预置菜品初始化数据（03_preset_dishes.sql）"
  echo "-- 来源库 : ${DB_NAME}@${DB_HOST}:${DB_PORT}   生成时间: $(date '+%F %T')"
  echo "-- 范围   : 1) life_dish_category 全量（先导，外键依赖）"
  echo "--         2) life_dish 全表（即预置菜品主表，含做法/轻量配方列）"
  echo "--         3) life_dish_ingredient 仅 owner_type='PRESET'"
  echo "-- 特性   : 保留原始 id；INSERT IGNORE 可安全重放；灌库顺序 01→02→03"
  echo "-- ============================================================="
  echo
  echo "SET NAMES utf8mb4;"
  echo "SET FOREIGN_KEY_CHECKS=0;"
  echo
  echo "-- ---- 1/3 菜品分类 life_dish_category ----"
  mysqldump "${DUMP_OPTS[@]}" -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}" life_dish_category
  echo
  echo "-- ---- 2/3 预置菜品 life_dish ----"
  mysqldump "${DUMP_OPTS[@]}" -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}" life_dish
  echo
  echo "-- ---- 3/3 预置配方食材 life_dish_ingredient（owner_type='PRESET'）----"
  mysqldump "${DUMP_OPTS[@]}" --where="owner_type='PRESET'" \
    -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}" life_dish_ingredient
  echo
  echo "SET FOREIGN_KEY_CHECKS=1;"
} > "${OUT}"

unset MYSQL_PWD

ROWS=$(grep -c "^INSERT" "${OUT}" || true)
echo "✓ 已导出: ${OUT}（共 ${ROWS} 条 INSERT）"
echo "  导入: mysql -h<host> -u<user> -p <库名> < ${OUT}"
echo "  建议: 放入 deploy/db/sql/ 并作为 03 号脚本纳入初始化链路"
