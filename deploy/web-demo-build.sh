#!/usr/bin/env bash
# 在服务器上构建「演示模式（mock）」的三个前端并发布到 /farm/（本机构建内存不够、上传产物太慢，所以传源码在服务器构建）。
# 用法：bash deploy/web-demo-build.sh [admin|m|steward|all]     默认 all
# - 源码（不含 node_modules / dist* / .git）同步到 /opt/farmer-saas/web-src/<目录>，依赖用 npmmirror
# - nice 10 + Node 堆 4G，避免挤占 training / ziheng
# - 产物发布到 /opt/farmer-saas/web/{admin,m,steward}/（旧的先备份成 releases/*-before-*.tgz）
# - 对应 nginx：/farm/ /farm/m/ /farm/steward/（deploy/server/farm-locations.inc）
set -euo pipefail
WHAT=${1:-all}
HOST=root@121.40.221.126
SSH="ssh -i $HOME/.ssh/id_ed25519 -o BatchMode=yes"
SCP="scp -q -i $HOME/.ssh/id_ed25519"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
quiet() { grep -v "post-quantum\|store now\|pq.html" || true; }

targets=""
case "$WHAT" in
  all) targets="admin m steward" ;;
  admin|m|steward) targets="$WHAT" ;;
  *) echo "用法：bash deploy/web-demo-build.sh [admin|m|steward|all]"; exit 2 ;;
esac

dirs=""
for t in $targets; do
  case $t in admin) dirs="$dirs web-admin" ;; m) dirs="$dirs app-member" ;; steward) dirs="$dirs app-steward" ;; esac
done
tar -czf "$TMP/src.tgz" -C "$ROOT" --exclude=node_modules --exclude='dist*' --exclude=.git $dirs
$SCP "$TMP/src.tgz" "$HOST:/tmp/web-demo-src.tgz" 2>&1 | quiet
rm -rf "$TMP"

$SSH "$HOST" "TARGETS='$targets' bash -s" <<'REMOTE' 2>&1 | quiet
set -e
export PATH=/opt/farmer-saas/node/bin:$PATH
S=/opt/farmer-saas/web-src
W=/opt/farmer-saas/web
R=/opt/farmer-saas/releases
mkdir -p $S $W $R && cd $S
for t in $TARGETS; do
  case $t in admin) d=web-admin ;; m) d=app-member ;; steward) d=app-steward ;; esac
  rm -rf $d/src $d/public $d/build $d/pages $d/sheep $d/static $d/scripts
done
tar -xzf /tmp/web-demo-src.tgz && rm -f /tmp/web-demo-src.tgz
PNPM="npx --yes pnpm@10.10.0"
publish() { # $1=产物目录 $2=发布目录名
  [ -d $W/$2 ] && tar -czf $R/$2-before-$(date +%Y%m%d%H%M%S).tgz -C $W $2
  rm -rf $W/$2 && cp -r "$1" $W/$2 && chmod -R a+rX $W/$2
  echo "已发布 $W/$2"
}
for t in $TARGETS; do
  case $t in
  admin)
    echo "== admin：安装依赖并构建（mode=demo）"
    cd $S/web-admin
    nice -n 10 $PNPM install --frozen-lockfile --registry=https://registry.npmmirror.com --reporter=silent 2>&1 | tail -3
    NODE_OPTIONS=--max_old_space_size=4096 nice -n 10 node ./node_modules/vite/bin/vite.js build --mode demo 2>&1 | tail -2
    grep -q 'src="/farm/assets/' dist-demo/index.html && echo "资源路径 /farm/ ✔"
    ! grep -rqF "localhost:48090" dist-demo && echo "没有 mock 地址 ✔"
    publish dist-demo admin ;;
  m)
    echo "== m（C 端）：安装依赖并构建（mode=demo）"
    cd $S/app-member
    nice -n 10 npm ci --registry=https://registry.npmmirror.com --no-audit --no-fund --loglevel=error 2>&1 | tail -3
    NODE_OPTIONS=--max_old_space_size=4096 nice -n 10 node scripts/uni.cjs build --mode demo 2>&1 | tail -3
    O=dist/build/h5
    grep -q '/farm/m/assets/' $O/index.html && echo "资源路径 /farm/m/ ✔"
    ! grep -rqF "localhost:48090" $O && echo "没有 mock 地址 ✔"
    publish $O m ;;
  steward)
    echo "== steward（管家端）：安装依赖并构建（mode=demo）"
    cd $S/app-steward
    nice -n 10 $PNPM install --frozen-lockfile --registry=https://registry.npmmirror.com --reporter=silent 2>&1 | tail -3
    NODE_OPTIONS=--max_old_space_size=4096 nice -n 10 $PNPM exec uni build --mode demo 2>&1 | tail -3
    O=dist/build/h5
    grep -q '/farm/steward/assets/' $O/index.html && echo "资源路径 /farm/steward/ ✔"
    ! grep -rqF "localhost:48090" $O && echo "没有 mock 地址 ✔"
    publish $O steward ;;
  esac
done
REMOTE
