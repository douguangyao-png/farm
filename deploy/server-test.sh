#!/usr/bin/env bash
# 在服务器上跑 mock-server 的测试和类型检查（用户 2026-11-05：「所有的测试，不要在本地运行。去服务器运行测试」）
# 用法：bash deploy/server-test.sh
# - 把当前工作区的 mock-server 源码（不含 node_modules）和 docs/ 同步到服务器 /opt/farmer-saas/ci/
# - 用服务器上的 Node 22（/opt/farmer-saas/node）装依赖（npmmirror），跑 tsc 和 vitest
# - 测试目录和运行中的 farm-mock 服务完全分开，不影响线上；supertest 不占固定端口
set -euo pipefail

HOST=root@121.40.221.126
SSH="ssh -i $HOME/.ssh/id_ed25519 -o BatchMode=yes"
SCP="scp -q -i $HOME/.ssh/id_ed25519"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
quiet() { grep -v "post-quantum\|store now\|pq.html" || true; }

tar -czf "$TMP/src.tgz" -C "$ROOT" --exclude=node_modules --exclude=dist \
  mock-server/src mock-server/test mock-server/seed mock-server/package.json mock-server/package-lock.json mock-server/tsconfig.json \
  $(cd "$ROOT" && ls mock-server/vitest.config.* 2>/dev/null) docs
$SCP "$TMP/src.tgz" "$HOST:/tmp/farm-ci-src.tgz" 2>&1 | quiet
rm -rf "$TMP"

$SSH "$HOST" 'set -e
export PATH=/opt/farmer-saas/node/bin:$PATH
mkdir -p /opt/farmer-saas/ci && cd /opt/farmer-saas/ci
# 保留 node_modules，只替换源码，依赖没变时跳过安装
rm -rf mock-server/src mock-server/test mock-server/seed docs
tar -xzf /tmp/farm-ci-src.tgz && rm -f /tmp/farm-ci-src.tgz
cd mock-server
if [ ! -d node_modules ] || ! cmp -s package-lock.json node_modules/.farm-lock; then
  echo "== 安装依赖（npmmirror）"
  npm ci --registry=https://registry.npmmirror.com --no-audit --no-fund --loglevel=error
  cp package-lock.json node_modules/.farm-lock
fi
echo "== node $(node -v)，tsc"
npx tsc --noEmit && echo "tsc: 无错误"
echo "== vitest"
nice -n 10 npx vitest run 2>&1 | tail -8' 2>&1 | quiet
