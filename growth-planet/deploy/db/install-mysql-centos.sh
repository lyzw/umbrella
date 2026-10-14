#!/usr/bin/env bash
#
# 成长星球 · CentOS 安装 MySQL 8.4 LTS（自建发布库）
# ----------------------------------------------------------------
# 适用：CentOS 7 / CentOS Stream 8 / 9（自动识别大版本选对应仓库）。
# 产物：MySQL 8.4 + 库 growth_planet(utf8mb4/utf8mb4_0900_ai_ci) + 独立应用账号。
# 用法：
#   chmod +x install-mysql-centos.sh
#   # 推荐：让脚本生成强密码并打印（务必保存）
#   sudo ./install-mysql-centos.sh
#   # 或显式指定（密码需满足 MySQL 默认策略：≥8 位、大小写+数字+特殊字符）
#   sudo ROOT_PWD='Str0ng#Root2026' APP_DB_PASSWORD='Str0ng#App2026' ./install-mysql-centos.sh
#   # 若应用部署在其它机器，需远程连库：
#   sudo BIND_ALL=1 ./install-mysql-centos.sh
#
# 装完后，把 deploy/db/sql/ 下脚本灌入即可（见末尾提示）：
#   DB_HOST=127.0.0.1 DB_USER=growthplanet DB_PASSWORD=xxx ./init.sh
#
# 说明：应用侧 application.yml 当前把数据源硬编码为 root/root，生产应外部化：
#   把 username/password 改为 ${DB_USER}/${DB_PASSWORD} 并注入环境变量。
# ----------------------------------------------------------------
set -euo pipefail

# ---- 可配置参数（环境变量覆盖）----
ROOT_PWD="${ROOT_PWD:-}"
APP_DB_PASSWORD="${APP_DB_PASSWORD:-}"
APP_DB_USER="${APP_DB_USER:-growthplanet}"
DB_NAME="${DB_NAME:-growth_planet}"
BIND_ALL="${BIND_ALL:-0}"

# ---- 1) 识别 CentOS 大版本 ----
if [[ -r /etc/os-release ]]; then
  . /etc/os-release
  VERSION_ID="${VERSION_ID:-0}"
fi
if [[ "${VERSION_ID}" == "7"* ]]; then EL=el7
elif [[ "${VERSION_ID}" == "8"* ]]; then EL=el8
elif [[ "${VERSION_ID}" == "9"* ]]; then EL=el9
else
  echo "✗ 无法识别 CentOS 版本（VERSION_ID=${VERSION_ID}），请手动指定 EL（el7/el8/el9）。" >&2
  exit 1
fi
echo "→ 检测到 CentOS 大版本对应仓库：${EL}"

# ---- 2) 生成强密码（若未提供）----
gen_pwd() { tr -dc 'A-Za-z0-9@#%^&*' < /dev/urandom | head -c 20; }
if [[ -z "${ROOT_PWD}" ]]; then ROOT_PWD="$(gen_pwd)"; fi
if [[ -z "${APP_DB_PASSWORD}" ]]; then APP_DB_PASSWORD="$(gen_pwd)"; fi

# ---- 3) 安装官方 MySQL Yum 仓库并安装服务端 ----
PM=$(command -v dnf || command -v yum)
echo "→ 使用包管理器：${PM}"
REPO_RPM="https://dev.mysql.com/get/mysql84-community-release-${EL}-1.noarch.rpm"
echo "→ 安装仓库：${REPO_RPM}"
"${PM}" -y install "${REPO_RPM}"

echo "→ 安装 mysql-community-server（8.4 LTS）..."
"${PM}" -y install mysql-community-server

# ---- 4) 启动并设置开机自启 ----
systemctl enable --now mysqld
# 等待 socket 就绪
for i in $(seq 1 30); do
  [[ -S /var/lib/mysql/mysql.sock ]] && break
  sleep 1
done

# ---- 5) 取临时 root 密码并改密 ----
TMP_PWD=$(grep 'temporary password' /var/log/mysqld.log | tail -1 | awk '{print $NF}')
echo "→ 使用临时密码重置 root 密码..."
mysql -uroot -p"${TMP_PWD}" --connect-expired-password <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY '${ROOT_PWD}';
SQL

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
  # 监听所有网卡
  if ! grep -q '^bind-address' /etc/my.cnf.d/mysql-server.cnf 2>/dev/null; then
    echo "bind-address=0.0.0.0" >> /etc/my.cnf.d/mysql-server.cnf
  else
    sed -i 's/^bind-address=.*/bind-address=0.0.0.0/' /etc/my.cnf.d/mysql-server.cnf
  fi
  systemctl restart mysqld
  # 防火墙放行 3306
  if command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
    firewall-cmd --add-port=3306/tcp --permanent
    firewall-cmd --reload
    echo "→ 已放行 3306/tcp（firewalld）"
  fi
fi

# ---- 8) 输出凭据（部署后请妥善保存并删除本输出）----
echo
echo "============================================================"
echo " MySQL 8.4 安装完成"
echo " 版本   : $(mysql -uroot -p"${ROOT_PWD}" -N -e 'SELECT VERSION();' 2>/dev/null)"
echo " 数据库 : ${DB_NAME}  (utf8mb4 / utf8mb4_0900_ai_ci)"
echo " root   : 密码见下方（请保存）—— ${ROOT_PWD}"
echo " 应用账号: ${APP_DB_USER} / ${APP_DB_PASSWORD}"
echo "============================================================"
echo
echo "下一步：将 deploy/db/sql/ 灌入该库（应用侧请外部化数据源为该应用账号）："
echo "  DB_HOST=127.0.0.1 DB_USER=${APP_DB_USER} DB_PASSWORD=${APP_DB_PASSWORD} ./init.sh"
