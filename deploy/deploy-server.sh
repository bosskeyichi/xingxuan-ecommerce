#!/usr/bin/env bash
# ============================================================
# 星选商城 · 服务器（Ubuntu/Debian）一键部署脚本
#
# 用法（在项目根目录或任意位置执行均可）：
#   bash deploy/deploy-server.sh            # 构建并启动全部服务
#   bash deploy/deploy-server.sh rebuild    # 强制重新构建镜像后启动
#   bash deploy/deploy-server.sh status     # 查看容器与健康状态
#   bash deploy/deploy-server.sh logs       # 跟踪网关 + 后端日志
#   bash deploy/deploy-server.sh down       # 停止容器（保留数据卷）
#
# 前置条件：Docker 24+ 与 Compose v2（本脚本会先自检）
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ACTION="${1:-up}"
GATEWAY_PORT="$(grep -E '^GATEWAY_PORT=' .env 2>/dev/null | cut -d= -f2 || true)"
GATEWAY_PORT="${GATEWAY_PORT:-8080}"

log()  { printf '\033[36m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[deploy]\033[0m %s\n' "$*"; }
die()  { printf '\033[31m[deploy]\033[0m %s\n' "$*" >&2; exit 1; }

require_docker() {
    command -v docker >/dev/null 2>&1 || die "未检测到 docker，请先安装：https://docs.docker.com/engine/install/ubuntu/"
    docker compose version >/dev/null 2>&1 || die "未检测到 docker compose 插件（v2）"
}

prepare_env() {
    if [ ! -f .env ]; then
        log "未发现 .env，按模板生成并随机化 JWT 密钥"
        cp .env.example .env
        if command -v openssl >/dev/null 2>&1; then
            secret="$(openssl rand -base64 48 | tr -d '\n')"
            sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${secret}|" .env
        else
            warn "未找到 openssl，沿用模板中的 JWT 密钥（仅适用于开发环境，生产请手动替换）"
        fi
    fi
    GATEWAY_PORT="$(grep -E '^GATEWAY_PORT=' .env | cut -d= -f2)"
    GATEWAY_PORT="${GATEWAY_PORT:-8080}"
}

wait_healthy() {
    local url="http://127.0.0.1:${GATEWAY_PORT}/health"
    log "等待网关就绪：${url}（最长 240 秒）"
    for _ in $(seq 1 120); do
        if curl -fsS -m 3 "$url" >/dev/null 2>&1; then
            log "网关已就绪"
            return 0
        fi
        sleep 2
    done
    warn "网关未在预期时间内就绪，请执行 'docker compose ps' 与 'docker compose logs gateway backend' 排查"
    return 1
}

show_summary() {
    local ip
    ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
    log "容器状态："
    docker compose ps
    printf '\n'
    log "访问地址："
    printf '  用户端   http://%s:%s/\n' "$ip" "$GATEWAY_PORT"
    printf '  管理端   http://%s:%s/admin/\n' "$ip" "$GATEWAY_PORT"
    printf '  API      http://%s:%s/api/health\n' "$ip" "$GATEWAY_PORT"
    printf '  内置账号 admin / 123456（管理员），alice / 123456（普通用户）\n'
}

require_docker

case "$ACTION" in
    up|rebuild)
        prepare_env
        if [ "$ACTION" = "rebuild" ]; then
            log "重新构建全部镜像"
            docker compose build --no-cache
        fi
        log "构建并启动 5 个服务（mysql / gateway / backend / frontend-user / frontend-admin）"
        docker compose up -d --build
        wait_healthy || true
        show_summary
        ;;
    status)
        prepare_env
        docker compose ps
        curl -fsS -m 5 "http://127.0.0.1:${GATEWAY_PORT}/health" && printf '\n'
        ;;
    logs)
        docker compose logs -f --tail=120 gateway backend
        ;;
    down)
        log "停止容器（数据卷保留，数据不会丢失）"
        docker compose down
        ;;
    *)
        die "未知参数：$ACTION（可用：up | rebuild | status | logs | down）"
        ;;
esac
