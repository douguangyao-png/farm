#!/usr/bin/env bash
# 演示环境一键发布（docs/plans/deploy-演示环境-121.40.221.126-2026-11-05.md）
# 用法：bash deploy/deploy.sh [all|mock|web|admin|m|steward]   默认 all（admin/m/steward 只发对应的一个前端）
# - mock：打包 mock 服务 + seed + docs/，上传并重启 farm-mock
# - web ：用 demo 模式构建三个前端，上传覆盖 /opt/farmer-saas/web/
# 每次发布前在服务器 /opt/farmer-saas/releases/ 留旧版本包，回滚见方案第 4 节。
# 不改 nginx（nginx 片段只在首次部署时装过一次）。
set -euo pipefail

WHAT=${1:-all}
HOST=root@121.40.221.126
SSH="ssh -i $HOME/.ssh/id_ed25519 -o BatchMode=yes"
SCP="scp -q -i $HOME/.ssh/id_ed25519"
ROOT=$(cd "$(dirname "$0")/.." && pwd)
T=$(date +%Y%m%d-%H%M%S)
TMP=$(mktemp -d)
PNPM="corepack pnpm@10.10.0"
export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
quiet() { grep -v "post-quantum\|store now\|pq.html" || true; }

deploy_mock() {
  echo "== mock：在服务器上跑测试（不在本机跑，用户 2026-11-05 定）"
  bash "$ROOT/deploy/server-test.sh" | tee "$TMP/test.log"
  grep -q "tsc: 无错误" "$TMP/test.log" && grep -Eq "Tests .*passed" "$TMP/test.log" && ! grep -Eq "Tests .*failed" "$TMP/test.log"     || { echo "服务器上的测试没通过，停止发布"; exit 1; }
  echo "== mock：打包（esbuild，不跑测试）"
  (cd "$ROOT/mock-server" && npm run -s bundle >/dev/null)
  tar -czf "$TMP/mock.tgz" -C "$ROOT/mock-server" dist/server.mjs seed
  tar -czf "$TMP/docs.tgz" -C "$ROOT" docs
  $SCP "$TMP/mock.tgz" "$TMP/docs.tgz" "$HOST:/tmp/" 2>&1 | quiet
  $SSH "$HOST" "set -e
    tar -czf /opt/farmer-saas/releases/mock-before-$T.tgz -C /opt/farmer-saas mock-server \$( [ -d /opt/farmer-saas/docs ] && echo docs )
    rm -rf /opt/farmer-saas/mock-server/* && tar -xzf /tmp/mock.tgz -C /opt/farmer-saas/mock-server
    rm -rf /opt/farmer-saas/docs && tar -xzf /tmp/docs.tgz -C /opt/farmer-saas
    rm -f /tmp/mock.tgz /tmp/docs.tgz
    chmod -R a+rX /opt/farmer-saas/mock-server /opt/farmer-saas/docs
    systemctl restart farm-mock; sleep 2; systemctl is-active farm-mock
    curl -s 127.0.0.1:48090/__mock/health" 2>&1 | quiet
  echo
}

deploy_web() {
  echo "== web：demo 模式构建三个前端（后台约 4 分钟）"
  local ends="${1:-admin m steward}"
  [[ " $ends " == *" admin "* ]] && (cd "$ROOT/web-admin" && $PNPM build:demo >/dev/null)
  [[ " $ends " == *" m "* ]] && (cd "$ROOT/app-member" && npm run -s build:h5:demo >/dev/null)
  [[ " $ends " == *" steward "* ]] && (cd "$ROOT/app-steward" && $PNPM build:h5:demo >/dev/null)
  echo "== 产物检查：不能有 localhost:48090，资源路径要带子路径"
  for d in web-admin/dist-demo app-member/dist/build/h5 app-steward/dist/build/h5; do
    if grep -rqF "localhost:48090" "$ROOT/$d"; then echo "产物 $d 里有 localhost:48090，停止发布"; exit 1; fi
  done
  grep -q 'src="/farm/assets/' "$ROOT/web-admin/dist-demo/index.html"
  grep -q '/farm/m/assets/' "$ROOT/app-member/dist/build/h5/index.html"
  grep -q '/farm/steward/assets/' "$ROOT/app-steward/dist/build/h5/index.html"
  git -C "$ROOT" diff --quiet -- app-member/manifest.json || { echo "app-member/manifest.json 没有还原，停止发布"; exit 1; }
  mkdir -p "$TMP/web"
  [[ " $ends " == *" admin "* ]] && cp -r "$ROOT/web-admin/dist-demo" "$TMP/web/admin"
  [[ " $ends " == *" m "* ]] && cp -r "$ROOT/app-member/dist/build/h5" "$TMP/web/m"
  [[ " $ends " == *" steward "* ]] && cp -r "$ROOT/app-steward/dist/build/h5" "$TMP/web/steward"
  tar -czf "$TMP/web.tgz" -C "$TMP/web" $ends
  $SCP "$TMP/web.tgz" "$HOST:/tmp/web-$T.tgz" 2>&1 | quiet
  $SSH "$HOST" "set -e
    tar -czf /opt/farmer-saas/releases/web-before-$T.tgz -C /opt/farmer-saas web
    rm -rf /tmp/farmweb && mkdir /tmp/farmweb && tar -xzf /tmp/web-$T.tgz -C /tmp/farmweb
    for d in $ends; do rm -rf /opt/farmer-saas/web/\$d && mv /tmp/farmweb/\$d /opt/farmer-saas/web/\$d; done
    mv /tmp/web-$T.tgz /opt/farmer-saas/releases/web-$T.tgz && rm -rf /tmp/farmweb
    chmod -R a+rX /opt/farmer-saas/web" 2>&1 | quiet
}

case "$WHAT" in
  mock) deploy_mock ;;
  web) deploy_web ;;
  admin|m|steward) deploy_web "$WHAT" ;;
  all) deploy_mock; deploy_web ;;
  *) echo "用法：bash deploy/deploy.sh [all|mock|web]"; exit 2 ;;
esac

echo "== 公网回归（状态码）"
for u in / /training/ /ziheng/ /farm/ /farm/m/ /farm/steward/ /farm-api/__mock/reset; do
  printf "%-24s %s\n" "$u" "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 10 https://121.40.221.126$u)"
done
TOK=$(curl -sk -H "tenant-id: 2" -H "Content-Type: application/json" -d '{"username":"qingshan","password":"demo123"}' \
  https://121.40.221.126/farm-api/admin-api/system/auth/login | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
printf "登录后 farm/dashboard/summary: "
curl -sk -H "tenant-id: 2" -H "Authorization: Bearer $TOK" https://121.40.221.126/farm-api/admin-api/farm/dashboard/summary | head -c 80; echo
rm -rf "$TMP"
echo "== 完成 $T。浏览器里登录后的页面需要人工走一遍。"
