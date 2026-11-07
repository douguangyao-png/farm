#!/usr/bin/env bash
# 在 121 服务器上安装真后端的运行环境（docs/plans/原型对齐与真后端-2026-11-06.md 第 5 节）。
# 前提：已经跑过 bash deploy/backend-build.sh（源码在 /opt/farmer-saas/backend-src，jar 已构建）。
# 用法：bash deploy/backend-install.sh [redis|db|service|all]     （默认 all）
#   redis   新建独立 Redis 实例 farm-redis（127.0.0.1:6380，带口令），不碰服务器上 6379 的那个
#   db      初始化 farm 库：芋道 ruoyi-vue-pro.sql → farm.sql → farm-seed.sql（库里已有 farm_base 表就跳过，FORCE=1 才重灌）
#   service 安装并启动 farm-backend.service（127.0.0.1:48080，profile=farm）。不改 nginx，现有 mock(48090) 不受影响
# 回滚：见脚本末尾的输出，或方案文件第 5 节。
set -euo pipefail

HOST=root@121.40.221.126
SSH="ssh -i $HOME/.ssh/id_ed25519 -o BatchMode=yes"
STAGE=${1:-all}
quiet() { grep -v "post-quantum\|store now\|pq.html" || true; }

$SSH "$HOST" "STAGE=$STAGE FORCE=${FORCE:-0} bash -s" <<'REMOTE' 2>&1 | quiet
set -euo pipefail
B=/opt/farmer-saas/backend
SRC=/opt/farmer-saas/backend-src/backend
JAVA=/usr/lib/jvm/jre-21/bin/java

if [ "$STAGE" = redis ] || [ "$STAGE" = all ]; then
  echo "== farm-redis（6380）"
  mkdir -p $B/redis/data
  if [ ! -f $B/redis.env ]; then
    PW=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24)
    ( umask 077; printf 'FARM_REDIS_HOST=127.0.0.1\nFARM_REDIS_PORT=6380\nFARM_REDIS_PASSWORD=%s\n' "$PW" > $B/redis.env )
  fi
  PW=$(grep FARM_REDIS_PASSWORD $B/redis.env | cut -d= -f2)
  ( umask 077; cat > $B/redis/redis.conf <<CONF
bind 127.0.0.1
port 6380
requirepass $PW
dir $B/redis/data
appendonly no
save ""
maxmemory 128mb
maxmemory-policy allkeys-lru
CONF
  )
  chown -R redis:redis $B/redis 2>/dev/null || true
  cat > /etc/systemd/system/farm-redis.service <<UNIT
[Unit]
Description=Farm SaaS Redis (6380)
After=network.target

[Service]
User=redis
Group=redis
ExecStart=/usr/bin/redis-server $B/redis/redis.conf
Restart=on-failure

[Install]
WantedBy=multi-user.target
UNIT
  systemctl daemon-reload
  systemctl enable --now farm-redis
  sleep 1
  REDISCLI_AUTH=$PW redis-cli -p 6380 ping
  echo "原 6379 Redis 状态：$(systemctl is-active redis)"
fi

if [ "$STAGE" = db ] || [ "$STAGE" = all ]; then
  echo "== 初始化 farm 库"
  set -a; . $B/db.env; set +a
  export PGPASSWORD=$FARM_DB_PASSWORD
  P="psql -h 127.0.0.1 -U farm -d farm -v ON_ERROR_STOP=1 -q"
  EXISTS=$($P -tAc "select to_regclass('public.farm_base') is not null")
  if [ "$EXISTS" = t ] && [ "${FORCE:-0}" != 1 ]; then
    echo "farm_base 表已存在，跳过（要重灌：FORCE=1 bash deploy/backend-install.sh db）"
  else
    mkdir -p $B/backup
    if [ "$EXISTS" = t ]; then pg_dump -h 127.0.0.1 -U farm -d farm -Fc -f $B/backup/farm-before-$(date +%Y%m%d%H%M%S).dump; echo "已备份旧库"; fi
    echo "-- 芋道自带表与数据"; $P -f $SRC/sql/postgresql/ruoyi-vue-pro.sql >/dev/null
    echo "-- 业务表";          $P -f $SRC/sql/postgresql/farm.sql
    echo "-- 演示数据";        $P -f $SRC/sql/postgresql/farm-seed.sql
    $P -tAc "select 'tenant='||(select count(*) from system_tenant)||' users='||(select count(*) from system_users)||' menus='||(select count(*) from system_menu)||' orders='||(select count(*) from farm_order)||' products='||(select count(*) from farm_product)"
  fi
fi

if [ "$STAGE" = service ] || [ "$STAGE" = all ]; then
  echo "== farm-backend.service（48080）"
  cp -f $SRC/yudao-server/target/yudao-server.jar $B/yudao-server.jar.new
  [ -f $B/yudao-server.jar ] && cp -f $B/yudao-server.jar $B/yudao-server.jar.prev
  mv -f $B/yudao-server.jar.new $B/yudao-server.jar
  cat > /etc/systemd/system/farm-backend.service <<UNIT
[Unit]
Description=Farm SaaS backend (yudao + farm module)
After=network.target postgresql.service farm-redis.service

[Service]
WorkingDirectory=$B
EnvironmentFile=$B/db.env
EnvironmentFile=$B/redis.env
ExecStart=$JAVA -Xms256m -Xmx768m -XX:+ExitOnOutOfMemoryError -jar $B/yudao-server.jar --spring.profiles.active=farm
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
UNIT
  systemctl daemon-reload
  systemctl enable farm-backend
  systemctl restart farm-backend
  echo "等待启动…"
  for i in $(seq 1 60); do
    if ss -ltn | grep -q ':48080 '; then echo "48080 已监听（${i}x3 秒）"; break; fi
    sleep 3
  done
  systemctl is-active farm-backend || true
  journalctl -u farm-backend --no-pager -n 15 | cut -c1-220
fi

echo "== 其他服务现状"
systemctl is-active training ziheng-biz ziheng-review nginx farm-mock postgresql redis | tr '\n' ' '; echo
echo "回滚：systemctl disable --now farm-backend farm-redis; rm /etc/systemd/system/farm-{backend,redis}.service; systemctl daemon-reload; DB 回滚见方案文件第 5 节"
REMOTE
