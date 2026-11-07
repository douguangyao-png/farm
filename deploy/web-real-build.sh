#!/usr/bin/env bash
# 在服务器上构建「真后端模式」的后台前端并发布到 /farm-real/（本机构建内存不够，上传 30MB 产物太慢，所以传源码在服务器构建）。
# 用法：bash deploy/web-real-build.sh
# - 源码（不含 node_modules / dist*）同步到 /opt/farmer-saas/web-src/web-admin，依赖用 npmmirror
# - nice 10 + Node 堆 4G，避免挤占 training / ziheng
# - 产物 dist-real → /opt/farmer-saas/web/admin-real/（旧的先备份成 releases/admin-real-before-*.tgz）
set -euo pipefail
HOST=root@121.40.221.126
SSH="ssh -i $HOME/.ssh/id_ed25519 -o BatchMode=yes"
SCP="scp -q -i $HOME/.ssh/id_ed25519"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
quiet() { grep -v "post-quantum\|store now\|pq.html" || true; }
tar -czf "$TMP/web-admin.tgz" -C "$ROOT" --exclude=node_modules --exclude='dist*' --exclude=.git web-admin
$SCP "$TMP/web-admin.tgz" "$HOST:/tmp/web-admin-src.tgz" 2>&1 | quiet
rm -rf "$TMP"
$SSH "$HOST" 'bash -s' <<'REMOTE' 2>&1 | quiet
set -e
export PATH=/opt/farmer-saas/node/bin:$PATH
S=/opt/farmer-saas/web-src
mkdir -p $S && cd $S
rm -rf web-admin/src web-admin/public web-admin/build && tar -xzf /tmp/web-admin-src.tgz && rm -f /tmp/web-admin-src.tgz
cd web-admin
PNPM="npx --yes pnpm@10.10.0"
echo "== 安装依赖"
nice -n 10 $PNPM install --frozen-lockfile --registry=https://registry.npmmirror.com --reporter=silent 2>&1 | tail -5
echo "== 构建（mode=real）"
NODE_OPTIONS=--max_old_space_size=4096 nice -n 10 node ./node_modules/vite/bin/vite.js build --mode real 2>&1 | tail -4
test -f dist-real/index.html
echo "== 产物检查"
grep -q 'src="/farm-real/assets/' dist-real/index.html && echo "资源路径带 /farm-real/ ✔"
! grep -rqF "localhost:48090" dist-real && echo "没有 mock 地址 ✔"
mkdir -p /opt/farmer-saas/releases
[ -d /opt/farmer-saas/web/admin-real ] && tar -czf /opt/farmer-saas/releases/admin-real-before-$(date +%Y%m%d%H%M%S).tgz -C /opt/farmer-saas/web admin-real
rm -rf /opt/farmer-saas/web/admin-real && cp -r dist-real /opt/farmer-saas/web/admin-real && chmod -R a+rX /opt/farmer-saas/web/admin-real
echo "已发布到 /opt/farmer-saas/web/admin-real"
REMOTE
