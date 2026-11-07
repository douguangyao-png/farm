#!/usr/bin/env bash
# 把仓库里的 deploy/server/farm-locations.inc 装到服务器（带真后端入口）。先备份，nginx -t 不过就自动还原，不 reload。
# 用法：bash deploy/nginx-farm-real.sh
set -euo pipefail
HOST=root@121.40.221.126
SSH="ssh -i $HOME/.ssh/id_ed25519 -o BatchMode=yes"
SCP="scp -q -i $HOME/.ssh/id_ed25519"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
quiet() { grep -v "post-quantum\|store now\|pq.html" || true; }
$SCP "$ROOT/deploy/server/farm-locations.inc" "$HOST:/tmp/farm-locations.inc.new" 2>&1 | quiet
$SSH "$HOST" 'bash -s' <<'REMOTE' 2>&1 | quiet
set -e
T=$(date +%Y%m%d%H%M%S)
cp -a /etc/nginx/farm-locations.inc /etc/nginx/farm-locations.inc.bak-$T
cp /tmp/farm-locations.inc.new /etc/nginx/farm-locations.inc && rm -f /tmp/farm-locations.inc.new
if nginx -t 2>&1 | tail -1 | grep -q successful; then
  systemctl reload nginx; echo "nginx 已重载（备份 farm-locations.inc.bak-$T）"
else
  cp -a /etc/nginx/farm-locations.inc.bak-$T /etc/nginx/farm-locations.inc; nginx -t 2>&1 | tail -3; echo "nginx -t 没通过，已还原，未重载"; exit 1
fi
for u in / /farm/ /training/ /ziheng/; do printf "%-12s %s\n" $u "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 10 https://127.0.0.1$u)"; done
REMOTE
