#!/usr/bin/env bash
#
# 成长星球 · 后端服务启动脚本（growth-planet.jar）
# -------------------------------------------------------------------
# 适用：把本脚本、growth-planet.jar、.env 放在同一目录（如 /opt/growth-planet）。
# 功能：start / stop / restart / status，后台运行 + pid 文件 + 日志。
# 内存：默认按 512M 整机预算给 JVM 分配（见下方 JVM_* 变量），可按需调小。
#
# 用法：
#   ./start.sh start      # 启动
#   ./start.sh stop       # 停止
#   ./start.sh restart    # 重启
#   ./start.sh status     # 状态
#
# 前置依赖（重要）：
#   1) JDK 21 路径已写死为 /opt/jdk-21.0.12（如安装位置不同，可 export JAVA_HOME 覆盖）。
#   2) MySQL（已建 growth_planet 库 + growthplanet 账号）与 Redis 已就绪。
#   3) 当前 application.yml 数据源/Redis 仍硬编码 root/root + localhost，
#      .env 里的 DB_*/REDIS_* 暂不会生效，需先把数据源外部化（见仓库说明）。
#      仅 JWT/七牛/微信/console 等已用 ${...} 占位的变量可被 .env 注入。
# -------------------------------------------------------------------
set -uo pipefail

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="growth-planet"
APP_JAR="$(ls -1 "${APP_HOME}"/*.jar 2>/dev/null | head -1)"
PID_FILE="${APP_HOME}/${APP_NAME}.pid"
LOG_DIR="${APP_HOME}/logs"
LOG_FILE="${LOG_DIR}/${APP_NAME}.out"

# ---- 端口 / profile ----
APP_PORT="${APP_PORT:-19100}"
APP_PROFILE="${APP_PROFILE:-dev}"

# ---- 内存预算（512M 整机下的 JVM 配置；若同机还跑 Redis 请调小 JVM_HEAP）----
JVM_HEAP="${JVM_HEAP:-320m}"      # 堆上限（Xms=Xmx，避免运行时抖动）
JVM_META="${JVM_META:-96m}"       # 元空间上限
JVM_DIRECT="${JVM_DIRECT:-64m}"   # Netty/MySQL 驱动 DirectBuffer 上限
JVM_XSS="${JVM_XSS:-256k}"        # 每线程栈（512M 下减小，省内存）

# ---- 写死 JDK 路径（如需覆盖可 export JAVA_HOME）----
JAVA_HOME="${JAVA_HOME:-/opt/jdk-21.0.12}"
JAVA_BIN="${JAVA_HOME}/bin/java"
[[ -x "${JAVA_BIN}" ]] || { echo "✗ 未找到 java：${JAVA_BIN}，请确认 JDK 已安装或 export JAVA_HOME" >&2; exit 1; }
export JAVA_HOME

# ---- 加载 .env（Spring Boot 通过 ${VAR} 自动绑定 JWT/七牛/微信/console 等）----
if [[ -f "${APP_HOME}/.env" ]]; then
  set -a; . "${APP_HOME}/.env"; set +a
fi

# ---- JVM 参数（数组，避免空格/引号转义问题）----
JAVA_OPTS=(
  -server
  -Xms"${JVM_HEAP}" -Xmx"${JVM_HEAP}"
  -XX:MaxMetaspaceSize="${JVM_META}"
  -XX:CompressedClassSpaceSize=64m
  -Xss"${JVM_XSS}"
  -XX:MaxDirectMemorySize="${JVM_DIRECT}"
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+UseStringDeduplication
  -XX:TieredStopAtLevel=1
  -Djava.security.egd=file:/dev/./urandom
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath="${LOG_DIR}/oom.hprof"
  -Dspring.profiles.active="${APP_PROFILE}"
  -Dserver.port="${APP_PORT}"
)

start() {
  if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    echo "✗ 已在运行 (pid $(cat "${PID_FILE}"))"
    exit 1
  fi
  [[ -n "${APP_JAR}" ]] || { echo "✗ 未找到 jar（请把 growth-planet.jar 放到 ${APP_HOME}）" >&2; exit 1; }
  mkdir -p "${LOG_DIR}"
  echo "→ 启动 ${APP_NAME} (profile=${APP_PROFILE}, heap=${JVM_HEAP}, port=${APP_PORT}) ..."
  nohup "${JAVA_BIN}" "${JAVA_OPTS[@]}" -jar "${APP_JAR}" >> "${LOG_FILE}" 2>&1 &
  echo $! > "${PID_FILE}"
  sleep 3
  if kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    echo "✓ 已启动 pid $(cat "${PID_FILE}")"
    echo "  日志：${LOG_FILE}"
    echo "  访问：http://127.0.0.1:${APP_PORT}"
    echo "  查看启动进度：tail -f ${LOG_FILE}"
  else
    echo "✗ 启动失败，请查看日志：${LOG_FILE}"
    rm -f "${PID_FILE}"
    exit 1
  fi
}

stop() {
  [[ -f "${PID_FILE}" ]] || { echo "✗ 无 pid 文件，可能未运行"; exit 0; }
  local pid; pid="$(cat "${PID_FILE}")"
  echo "→ 停止 pid ${pid} ..."
  kill "${pid}" 2>/dev/null
  local i
  for i in $(seq 1 30); do kill -0 "${pid}" 2>/dev/null || break; sleep 1; done
  if kill -0 "${pid}" 2>/dev/null; then kill -9 "${pid}"; fi
  rm -f "${PID_FILE}"
  echo "✓ 已停止"
}

status() {
  if [[ -f "${PID_FILE}" ]] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
    echo "✓ 运行中 pid $(cat "${PID_FILE}")"
  else
    echo "✗ 未运行"
  fi
}

# 打印 .env 中声明的变量名（默认不泄露值）；-show/-v 打印脱敏值（前4后4）。
# 额外自检 JWT 密钥字节长度——prod 守卫要求 >=32 字节。
env_dump() {
  local show_values=0
  [[ "${2:-}" == "-show" || "${2:-}" == "-v" ]] && show_values=1
  local env_file="${APP_HOME}/.env"

  echo "==== .env 中声明的变量名（不泄露值）===="
  if [[ ! -f "${env_file}" ]]; then
    echo "  （未找到 ${env_file}）"
  else
    while IFS= read -r line; do
      line="$(echo "$line" | sed -e 's/[[:space:]]*$//')"
      [[ -z "$line" || "$line" == \#* ]] && continue
      [[ "$line" != *=* ]] && continue
      local name="${line%%=*}"
      name="$(echo "$name" | sed -e 's/^export[[:space:]]*//' -e 's/^[[:space:]]*//')"
      if [[ $show_values -eq 1 ]]; then
        local val="${!name:-}"
        local masked
        if [[ -z "$val" ]]; then masked="<空>"; else masked="${val:0:4}****${val: -4}"; fi
        printf '  %-30s = %s\n' "$name" "$masked"
      else
        printf '  %s\n' "$name"
      fi
    done < "${env_file}"
  fi

  echo
  echo "==== 当前进程环境中相关前缀变量名 ===="
  compgen -v 2>/dev/null | grep -iE '^(JWT|DB|REDIS|QINIU|WX|CONSOLE|APP|JVM|JAVA)_' | sort | sed 's/^/  /'

  echo
  echo "==== JWT 密钥字节长度自检（prod 守卫要求 >=32 字节）===="
  for v in JWT_SECRET JWT_ADMIN_SECRET; do
    local val="${!v:-}"
    if [[ -z "$val" ]]; then
      printf '  %-20s : <未设置>\n' "$v"
    else
      printf '  %-20s : %d 字节 %s\n' "$v" "${#val}" "$([[ ${#val} -ge 32 ]] && echo OK || echo '✗ 不足32')"
    fi
  done
}

case "${1:-start}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; start ;;
  status)  status ;;
  env)     env_dump "$@" ;;
  *) echo "用法: $0 {start|stop|restart|status|env [-show]}"; exit 1 ;;
esac
