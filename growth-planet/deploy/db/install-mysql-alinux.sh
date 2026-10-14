#!/usr/bin/env bash
#
# 成长星球 · Alibaba Cloud Linux 3（Anolis / RHEL 8 系）安装 MySQL 8.4 LTS（自建发布库）
# -----------------------------------------------------------------------------------
# 适用：Alibaba Cloud Linux 3 / Anolis OS 8 / Rocky / Alma / RHEL 8 / CentOS Stream 8/9
#       （统一锁定 MySQL 官方 el8 仓库；自动处理 RHEL8 系模块流冲突）
# 产物：MySQL 8.4 LTS + 库 growth_planet(utf8mb4/utf8mb4_0900_ai_ci) + 独立应用账号
#
# 用法（在目标服务器上以 root 执行）：
#   chmod +x install-mysql-alinux.sh
#   # 推荐：让脚本生成强密码并打印（务必保存）
#   sudo ./install-mysql-alinux.sh
#   # 或显式指定密码（需满足 MySQL 默认策略：≥8 位、大小写+数字+特殊字符）
#   sudo ROOT_PWD='Str0ng#Root2026' APP_DB_PASSWORD='Str0ng#App2026' ./install-mysql-alinux.sh
#   # 应用部署在其它机器，需要远程连库（会监听 0.0.0.0 + 放行 3306）
#   sudo BIND_ALL=1 ./install-mysql-alinux.sh
#   # 已装好 MySQL 仅需补建库/账号时：
#   sudo SKIP_INSTALL=1 ROOT_PWD='已有root密码' ./install-mysql-alinux.sh
#
# 装完后把 deploy/db/sql/ 灌入库（应用侧请把数据源外部化为该应用账号）：
#   DB_HOST=127.0.0.1 DB_USER=growthplanet DB_PASSWORD=xxx ./init.sh
# -----------------------------------------------------------------------------------
set -euo pipefail

# ---- 0) 必须 root ----
if [[ "${EUID}" -ne 0 ]]; then echo "✗ 请使用 root 执行（sudo bash $0）" >&2; exit 1; fi

# ---- 可配置参数（环境变量覆盖）----
ROOT_PWD="${ROOT_PWD:-}"
APP_DB_PASSWORD="${APP_DB_PASSWORD:-}"
APP_DB_USER="${APP_DB_USER:-growthplanet}"
DB_NAME="${DB_NAME:-growth_planet}"
BIND_ALL="${BIND_ALL:-0}"
SKIP_INSTALL="${SKIP_INSTALL:-0}"
FORCE="${FORCE:-0}"
# Alinux3 / Anolis8 等 RHEL8 系统一锁定 MySQL 官方 el8 仓库
EL="el8"

# ---- 1) 系统识别（确认是 RHEL8 系；否则需 FORCE=1）----
if [[ -r /etc/os-release ]]; then . /etc/os-release; fi
PRETTY="${PRETTY_NAME:-unknown}"
case "${ID} ${ID_LIKE} ${PLATFORM_ID}" in
  *alinux*|*anolis*|*rhel*|*centos*|*rocky*|*alma*|*fedora*)
    echo "→ 已识别系统：${PRETTY}（按 RHEL8 系处理，仓库=${EL}）" ;;
  *)
    if [[ "${FORCE}" != "1" ]]; then
      echo "✗ 未识别为 RHEL8 系（${PRETTY}）。若确定兼容请追加 FORCE=1 重试。" >&2
      exit 1
    fi
    echo "→ 非标准系统（${PRETTY}），FORCE=1，按 RHEL8 系处理。" ;;
esac

# ---- 2) 生成强密码（若未提供）----
gen_pwd() { LC_ALL=C tr -dc 'A-Za-z0-9@#%^&*' < /dev/urandom | head -c 20; }
if [[ -z "${ROOT_PWD}" ]]; then ROOT_PWD="$(gen_pwd)"; fi
if [[ -z "${APP_DB_PASSWORD}" ]]; then APP_DB_PASSWORD="$(gen_pwd)"; fi

PM=$(command -v dnf || command -v yum)
echo "→ 包管理器：${PM}"

# ---- 3) 安装 MySQL 官方仓库 + 服务端 ----
if [[ "${SKIP_INSTALL}" != "1" ]]; then
  # 关键：RHEL8 系默认带 mysql/mariadb 模块流，禁用以免装成 MariaDB 或与 mysql-community-libs 冲突
  ${PM} -y module disable mysql 2>/dev/null || true
  ${PM} -y module disable mariadb 2>/dev/null || true

  REPO_RPM="https://dev.mysql.com/get/mysql84-community-release-${EL}-1.noarch.rpm"
  echo "→ 安装 MySQL 官方仓库：${REPO_RPM}"
  ${PM} -y install "${REPO_RPM}"

  # 关键：Alibaba Cloud Linux 的 $releasever 可能解析成 3/2104，导致 el/3 找不到包，
  # 强制把仓库 baseurl 里的版本路径锁定为 el/8。
  for f in /etc/yum.repos.d/mysql-community.repo /etc/yum.repos.d/mysql-community-source.repo; do
    [[ -f "$f" ]] || continue
    if grep -q 'el/\$releasever\|el/3\|el/2104\|el/2' "$f"; then
      sed -i -E 's#(baseurl=https://repo\.mysql\.com/yum/mysql-[^/]+/)el/\$releasever/#\1el/8/#g' "$f"
      sed -i -E 's#(baseurl=https://repo\.mysql\.com/yum/mysql-[^/]+/)el/(3|2104|2)/#\1el/8/#g' "$f"
      echo "→ 已修正仓库版本路径为 el/8：$f"
    fi
  done

  echo "→ 安装 mysql-community-server（8.4 LTS）..."
  ${PM} -y install mysql-community-server
else
  echo "→ SKIP_INSTALL=1：跳过安装，仅做建库/建账号（使用现有 ROOT_PWD）"
fi

# ---- 4) 启动并设置开机自启 ----
systemctl enable --now mysqld
# 等待 socket 就绪
for i in $(seq 1 30); do
  [[ -S /var/lib/mysql/mysql.sock ]] && break
  sleep 1
done
[[ -S /var/lib/mysql/mysql.sock ]] || { echo "✗ mysqld 启动失败，请查 /var/log/mysqld.log" >&2; exit 1; }

# ---- 5) 取临时 root 密码并改密（仅首次安装需要）----
if [[ "${SKIP_INSTALL}" != "1" ]]; then
  TMP_PWD=$(grep 'temporary password' /var/log/mysqld.log | tail -1 | awk '{print $NF}')
  if [[ -z "${TMP_PWD}" ]]; then
    echo "⚠ 未找到临时密码（可能之前已初始化）。如 root 已有密码请改用 SKIP_INSTALL=1 ROOT_PWD=... 重试。" >&2
    exit 1
  fi
  echo "→ 使用临时密码重置 root 密码..."
  mysql -uroot -p"${TMP_PWD}" --connect-expired-password <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY '${ROOT_PWD}';
SQL
fi

# ---- 6) 基础安全加固（去匿名/禁远程 root/删 test 库）----
mysql -uroot -p"${ROOT_PWD}" <<SQL
DELETE FROM mysql.user WHERE User='';
DELETE FROM mysql.user WHERE User='root' AND Host NOT IN ('localhost','127.0.0.1','::1');
DROP DATABASE IF EXISTS test;
DELETE FROM mysql.db WHERE Db='test' OR Db='test\\_%';
FLUSH PRIVILEGES;
SQL

# ---- 7) 建库 + 独立应用账号 ----
mysql -uroot -p"${ROOT_PWD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${APP_DB_USER}'@'localhost' IDENTIFIED BY '${APP_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${APP_DB_USER}'@'localhost';
SQL

if [[ "${BIND_ALL}" == "1" ]]; then
  mysql -uroot -p"${ROOT_PWD}" -e "CREATE USER IF NOT EXISTS '${APP_DB_USER}'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}'; GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${APP_DB_USER}'@'%'; FLUSH PRIVILEGES;"
  CNF=/etc/my.cnf.d/mysql-server.cnf
  if ! grep -q '^bind-address' "${CNF}" 2>/dev/null; then
    echo "bind-address=0.0.0.0" >> "${CNF}"
  else
    sed -i 's/^bind-address=.*/bind-address=0.0.0.0/' "${CNF}"
  fi
  systemctl restart mysqld
  # 防火墙放行（Alinux3 默认 firewalld 多半未运行，未运行则跳过，但务必在安全组放行 3306）
  if command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
    firewall-cmd --add-port=3306/tcp --permanent
    firewall-cmd --reload
    echo "→ 已放行 3306/tcp（firewalld）"
  else
    echo "→ 未检测到运行中的 firewalld，跳过系统防火墙；请到阿里云『安全组』放行 3306（入方向）。"
  fi
fi

# ---- 8) 输出凭据（部署后请妥善保存并删除本输出）----
echo
echo "============================================================"
echo " MySQL 8.4 安装完成"
echo " 系统   : ${PRETTY}"
echo " 版本   : $(mysql -uroot -p"${ROOT_PWD}" -N -e 'SELECT VERSION();' 2>/dev/null)"
echo " 数据库 : ${DB_NAME}  (utf8mb4 / utf8mb4_0900_ai_ci)"
echo " root   : ${ROOT_PWD}"
echo " 应用账号: ${APP_DB_USER} / ${APP_DB_PASSWORD}"
echo "============================================================"
echo
echo "下一步：将 deploy/db/sql/ 灌入该库（应用侧请把数据源外部化为该应用账号）："
echo "  DB_HOST=127.0.0.1 DB_USER=${APP_DB_USER} DB_PASSWORD=${APP_DB_PASSWORD} ./init.sh"
if [[ "${BIND_ALL}" == "1" ]]; then
  echo "  远程连接请确保阿里云安全组已放行 3306 入方向，且 MySQL 用户 '${APP_DB_USER}'@'%' 已授权。"
fi
