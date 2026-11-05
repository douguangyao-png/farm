#!/usr/bin/env bash
# 新服务器一键上 HTTPS：签 Let's Encrypt 证书 + http 全部 308 跳 https
# 来源：farmer-saas/docs/plans/deploy-HTTPS-IP证书-2026-11-05.md（121.40.221.126 上手工做过一遍，这里固化成脚本）
#
# 用法（在本机执行，通过 SSH 操作目标服务器）：
#   bash deploy/https-setup.sh --host root@1.2.3.4                    # 用服务器 IP 签 IP 证书（6.5 天有效，自动续期）
#   bash deploy/https-setup.sh --host root@1.2.3.4 --domain a.com     # 用域名签普通证书（90 天，自动续期）
#   bash deploy/https-setup.sh --host root@1.2.3.4 --check            # 只检查，不改任何东西
# 可选：--key ~/.ssh/xxx（SSH 私钥，默认 ~/.ssh/id_ed25519）  --site /etc/nginx/conf.d/xx.conf（站点文件，默认自动找）
#
# 前提（脚本会先检查，不满足就停下，不会替你改）：
#   - 服务器装了 nginx，带 ssl 模块；只有一个监听 80 的站点 server 块（listen 80 default_server）
#   - 云安全组已放行 80、443（80 给证书验证用，不能关）；服务器防火墙放行 443
#   - IP 证书要求服务器有公网 IP 直接对外；域名证书要求域名已解析到这台服务器（国内服务器还要备案）
#
# 它会做：备份 nginx 配置 → 装 acme.sh（官方包）→ 80 端口加证书验证路径 → 签证书 → 站点改监听 443
#         → 新建 80 端口的跳转站点（308，保持请求方法和请求体）→ 每步 nginx -t 通过才 reload，失败自动还原
#         → 对比改前改后各路径状态码 → 打印回滚命令
# 不做：HSTS（证书短，续期失败时 HSTS 会让浏览器连「继续访问」都不给点）；不改防火墙和安全组。
set -euo pipefail

HOST="" KEY="$HOME/.ssh/id_ed25519" DOMAIN="" SITE="" CHECK_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST=$2; shift 2 ;;
    --key) KEY=$2; shift 2 ;;
    --domain) DOMAIN=$2; shift 2 ;;
    --site) SITE=$2; shift 2 ;;
    --check) CHECK_ONLY=1; shift ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "不认识的参数：$1（--help 看用法）"; exit 2 ;;
  esac
done
[ -n "$HOST" ] || { echo "缺少 --host，例如 --host root@1.2.3.4"; exit 2; }
IP=${HOST#*@}
NAME=${DOMAIN:-$IP}

echo "== 目标：$HOST  证书：$NAME（$([ -n "$DOMAIN" ] && echo 域名证书 || echo IP 证书)）  模式：$([ $CHECK_ONLY = 1 ] && echo 只检查 || echo 执行)"

ssh -i "$KEY" -o BatchMode=yes -o ConnectTimeout=15 "$HOST" \
  "NAME='$NAME' DOMAIN='$DOMAIN' SITE='$SITE' CHECK_ONLY='$CHECK_ONLY' bash -s" <<'REMOTE' 2>&1 | grep -v "post-quantum\|store now\|pq.html"
set -euo pipefail
say() { echo "   $*"; }
die() { echo "!! $*"; exit 1; }

# ---------- 1. 检查 ----------
echo "== 1/6 检查"
command -v nginx >/dev/null || die "没装 nginx"
nginx -V 2>&1 | grep -q http_ssl_module || die "nginx 没有 ssl 模块"
nginx -t >/dev/null 2>&1 || die "现在的 nginx 配置本身就有错误（nginx -t 不通过），先修好再来"
if [ -z "$SITE" ]; then
  SITE=$(grep -lE "^\s*listen\s+80\s+default_server" /etc/nginx/conf.d/*.conf /etc/nginx/nginx.conf 2>/dev/null | head -1 || true)
fi
[ -n "$SITE" ] && [ -f "$SITE" ] || die "找不到监听 80 default_server 的站点文件，用 --site 指定"
[ "$(grep -cE '^\s*server\s*\{' "$SITE")" = 1 ] || die "$SITE 里不止一个 server 块，脚本不处理这种情况，请手工改"
grep -qE "^\s*listen\s+80\s+default_server" "$SITE" || die "$SITE 里没有 listen 80 default_server"
if grep -qE "^\s*listen\s+443" "$SITE"; then die "$SITE 已经在监听 443，看起来已经配过 HTTPS"; fi
say "站点文件：$SITE"
if systemctl is-active -q firewalld 2>/dev/null; then
  if firewall-cmd --query-service=https -q 2>/dev/null || firewall-cmd --query-port=443/tcp -q 2>/dev/null; then say "防火墙已放行 443"; else die "firewalld 没放行 443（firewall-cmd --permanent --add-service=https && firewall-cmd --reload），脚本不替你改"; fi
fi
ss -ltn | grep -qE ":443\s" && die "443 已经有程序在监听"
FREE=$(df -Pm /etc | awk 'NR==2{print $4}'); [ "$FREE" -gt 100 ] || die "磁盘剩余不到 100MB"
paths=$(grep -oE "location\s+(=\s*|\^~\s*)?/[^ ]*" "$SITE" | awk '{print $NF}' | grep -v '^/$' | sort -u | head -12)
baseline() { for u in / $paths; do printf "%s %s\n" "$u" "$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$1$u")"; done; }
say "改之前（http）各路径状态码："; baseline http://127.0.0.1 | sed 's/^/     /'
if [ "$CHECK_ONLY" = 1 ]; then echo "== 只检查：全部通过，可以执行"; exit 0; fi

# ---------- 2. 备份 ----------
echo "== 2/6 备份"
B=/root/backup/https-$(date +%Y%m%d-%H%M%S); mkdir -p "$B"
cp -a /etc/nginx "$B/nginx"; baseline http://127.0.0.1 > "$B/baseline.txt"
say "备份在 $B（整个 /etc/nginx）"
restore() { echo "!! 出错，还原 nginx 配置"; rm -rf /etc/nginx && cp -a "$B/nginx" /etc/nginx && nginx -t && systemctl reload nginx; exit 1; }
reload_or_restore() { if nginx -t >/dev/null 2>&1; then systemctl reload nginx; say "nginx -t 通过，已 reload"; else nginx -t || true; restore; fi; }

# ---------- 3. acme.sh ----------
echo "== 3/6 acme.sh"
if [ ! -x /root/.acme.sh/acme.sh ]; then
  V=$(curl -s --max-time 20 https://api.github.com/repos/acmesh-official/acme.sh/releases/latest | sed -n 's/.*"tag_name": "\([^"]*\)".*/\1/p' | head -1)
  [ -n "$V" ] || V=3.1.6
  T=$(mktemp -d)
  # github.com 的 archive 地址在国内服务器上容易超时，直接走 codeload
  curl -sSfL --max-time 120 -o "$T/acme.tgz" "https://codeload.github.com/acmesh-official/acme.sh/tar.gz/refs/tags/$V" || die "下载 acme.sh $V 失败"
  tar -xzf "$T/acme.tgz" -C "$T" && (cd "$T/acme.sh-$V" && ./acme.sh --install --home /root/.acme.sh --no-profile >/dev/null)
  rm -rf "$T"
fi
say "$(/root/.acme.sh/acme.sh --version | tail -1)，续期任务：$(crontab -l 2>/dev/null | grep -c acme.sh) 条"

# ---------- 4. 证书验证路径 + 签证书 ----------
echo "== 4/6 签证书"
mkdir -p /var/www/acme/.well-known/acme-challenge && chmod -R a+rX /var/www/acme
if ! grep -q "acme-challenge" "$SITE"; then
  sed -i "0,/^\(\s*\)server_name[^;]*;/s||&\n\1# Let's Encrypt 证书验证（https-setup.sh）\n\1location ^~ /.well-known/acme-challenge/ { root /var/www/acme; default_type text/plain; }|" "$SITE"
fi
reload_or_restore
if [ -n "$DOMAIN" ]; then
  ISSUE_ARGS="--server letsencrypt -d $DOMAIN"
else
  ISSUE_ARGS="--server letsencrypt -d $NAME --cert-profile shortlived --days 3"   # IP 证书只能用 shortlived（160 小时）
fi
/root/.acme.sh/acme.sh --issue $ISSUE_ARGS -w /var/www/acme > /tmp/https-setup-issue.log 2>&1 || {
  grep -iE "error|fail" /tmp/https-setup-issue.log | tail -5; echo "!! 签证书失败（常见原因：安全组没放行 80、域名没解析到这台服务器）"; restore; }
ECC=$([ -d "/root/.acme.sh/${NAME}_ecc" ] && echo --ecc || echo "")
mkdir -p /etc/nginx/ssl && chmod 700 /etc/nginx/ssl
/root/.acme.sh/acme.sh --install-cert -d "$NAME" $ECC --key-file "/etc/nginx/ssl/$NAME.key" --fullchain-file "/etc/nginx/ssl/$NAME.crt" \
  --reloadcmd "nginx -t && systemctl reload nginx" >/dev/null 2>&1 || restore
say "$(openssl x509 -in "/etc/nginx/ssl/$NAME.crt" -noout -issuer -enddate | tr '\n' ' ')"

# ---------- 5. 切到 443 + 80 跳转 ----------
echo "== 5/6 切换 HTTPS"
python3 - "$SITE" "$NAME" <<'PY'
import re, sys
p, name = sys.argv[1], sys.argv[2]
s = open(p, encoding="utf-8").read()
lines = s.split("\n"); out = []; done = False
for ln in lines:
    m = re.match(r"^(\s*)listen\s+(\[::\]:)?80\s+default_server\s*;", ln)
    if m:
        ind, v6 = m.group(1), m.group(2) or ""
        out.append(f"{ind}listen {v6}443 ssl default_server;   # https-setup.sh：原 listen {v6}80")
        if not done:
            out += [f"{ind}http2 on;",
                    f"{ind}ssl_certificate     /etc/nginx/ssl/{name}.crt;   # Let's Encrypt，acme.sh 自动续期",
                    f"{ind}ssl_certificate_key /etc/nginx/ssl/{name}.key;",
                    f"{ind}ssl_protocols TLSv1.2 TLSv1.3;",
                    f"{ind}ssl_session_cache shared:SSL:10m;",
                    f"{ind}ssl_session_timeout 1d;"]
            done = True
        continue
    out.append(ln)
open(p, "w", encoding="utf-8").write("\n".join(out))
PY
cat > /etc/nginx/conf.d/zz-http-to-https.conf <<'EOF'
# https-setup.sh 生成：80 端口只保留 Let's Encrypt 证书验证，其余全部 308 跳 https（308 保持请求方法和请求体）
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;
    server_tokens off;
    location ^~ /.well-known/acme-challenge/ { root /var/www/acme; default_type text/plain; }
    location / { return 308 https://$host$request_uri; }
}
EOF
reload_or_restore
sleep 1

# ---------- 6. 验证 ----------
echo "== 6/6 验证"
say "http 跳转：$(curl -s -o /dev/null -w '%{http_code} -> %{redirect_url}' --max-time 10 http://127.0.0.1/)"
say "改之后（https，校验证书）与改之前（http）对比："
fail=0
while read -r u code; do
  now=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "https://$NAME$u" || echo ERR)
  mark=$([ "$now" = "$code" ] && echo "一致" || { fail=1; echo "不一致"; })
  printf "     %-22s 之前 %s  现在 %s  %s\n" "$u" "$code" "$now" "$mark"
done < "$B/baseline.txt"
[ $fail = 0 ] || echo "!! 有路径状态码变了，请检查；需要时用下面的回滚命令"
echo "== 完成。回滚命令："
echo "   rm -rf /etc/nginx && cp -a $B/nginx /etc/nginx && nginx -t && systemctl reload nginx"
echo "   /root/.acme.sh/acme.sh --uninstall-cronjob   # 停用自动续期（可选）"
echo "   提醒：切换前就打开着的 http 页面会报跨域错误，刷新一次即可；登录后的页面请人工走一遍"
REMOTE
