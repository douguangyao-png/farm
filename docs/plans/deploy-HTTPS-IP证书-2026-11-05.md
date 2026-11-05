# 121.40.221.126 整站上 HTTPS（Let's Encrypt IP 证书），http 全部跳转 https

- 类型：工程
- 档位：B
- 风险：高（改共享服务器的全局 nginx：实训中心、资衡、农场演示三个系统一起切到 https；证书 6.5 天有效，续期失败会让三个系统都出现证书警告）
- 状态：已完成
- 批准：用户 2026-11-05
- 关联：docs/plans/deploy-演示环境-121.40.221.126-2026-11-05.md；服务器 hp.conf 头部的「新服务器迁移方案 3.6」

## 1. 调研

- **目标：** 用户 2026-11-05：「如果443已经开通，需要集成ssl，http链接直接跳转到https」；证书选「Let's Encrypt IP 证书」，跳转范围选「整台服务器都跳转」；用途「给其他人测试」。
- **影响面：** nginx 唯一的站点 `hp.conf` 上的全部路径：`/` → `/training/`、`/training/`（127.0.0.1:8095）、`/ziheng/`（127.0.0.1:18096）、`/farm/`、`/farm/m/`、`/farm/steward/`、`/farm-api/`。清单来自 `cat /etc/nginx/conf.d/hp.conf`。
- **同类：** 服务器上没有任何已有的 SSL 配置（`grep ssl_certificate` 无结果，nginx.conf 里 443 的 server 块是注释掉的）。
- **改法：** 用 acme.sh 走 http-01 验证签 IP 证书（shortlived 档）；80 端口只留证书验证路径，其余 308 跳 https；原 server 块改为监听 443。

证据（2026-11-05）：
- [实测] 外部探测 443：一次「连接被拒」（包到了服务器、只是没有程序监听），一次超时（本机线路丢包 20%），说明阿里云安全组已放行 443；服务器 firewalld 已放行 443/tcp。
- [实测] nginx 1.26.3 带 `http_ssl_module`、`http_v2_module`；OpenSSL 3.5.8。
- [实测] `hp-proxy.inc` 已经转发 `X-Forwarded-Proto $scheme`，后端能知道外面是 https。
- [实测] 资衡的 location 里有 `proxy_cookie_flags ~ nosecure; # HTTP 期间临时，上 HTTPS 后删除`。这是资衡项目的配置，本方案不动，在记录里提醒资衡负责人。
- [文档] Let's Encrypt 2026-01-15 起正式签发 IP 地址证书，必须用 shortlived 档（有效期 160 小时）；acme.sh 需要 `--cert-profile shortlived`（https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability）。
- [实测] 服务器 `/root/acme.sh.install` 是来源不明的安装脚本（11-03 放的），不用它，改从 acme.sh 官方 GitHub 发布包安装。

## 2. 设计

- **证书**：acme.sh 装在 `/root/.acme.sh`，`--server letsencrypt --cert-profile shortlived`，http-01 webroot 验证（`/var/www/acme`）。证书安装到 `/etc/nginx/ssl/hp-ip.{crt,key}`，续期后自动 `systemctl reload nginx`。acme.sh 自带每日 cron，到期前 3 天内会续（`--days 3`，避免频繁请求被限流）。不登记邮箱（不把用户邮箱发给第三方）。
- **nginx**（顺序保证中途不断服务）：
  1. 先在现有 80 端口的 server 里加 `location ^~ /.well-known/acme-challenge/ { root /var/www/acme; }`，reload，签证书。
  2. 证书到手后：`hp.conf` 的 `listen 80` 改为 `listen 443 ssl` + 证书配置；新建 `conf.d/hp-http.conf`：80 端口 default_server，只保留证书验证路径，其余 `return 308 https://$host$request_uri;`。
- **308 而不是 301**：308 要求客户端保持请求方法和请求体，http 的 POST 跳过去仍是 POST。
- **不加 HSTS**：证书只有 6.5 天，万一续期失败，HSTS 会让浏览器连「继续访问」都不让点。
- **前端**：三个前端用的都是相对路径（`/farm-api`），不需要重新构建；C 端分享用的 `SHOPRO_H5_URL` 还是 http，会被 308 跳到 https，不影响使用，下次发布时改掉。

## 3. 任务

- [x] T1 备份 nginx 配置，记录基线
  - 验证：`/root/backup/https-<时间>/` 里有 `hp.conf`、`nginx.conf` 和 sha256；基线：`/`、`/training/`、`/ziheng/`、`/farm/` 的 http 状态码
- [x] T2 安装 acme.sh（官方发布包），80 端口加证书验证路径，签发 IP 证书
  - 验证：`nginx -t` 通过后 reload；`/` 等基线状态码不变；`openssl x509 -noout -subject -ext subjectAltName -dates` 显示 `IP Address:121.40.221.126`、签发者 Let's Encrypt、有效期约 160 小时；`crontab -l` 里有 acme.sh 的续期任务
- [x] T3 切到 https：hp.conf 改监听 443 ssl，新建 hp-http.conf 做 308 跳转
  - 验证：`nginx -t` 通过后 reload；`curl -I http://121.40.221.126/training/` 返回 308 且 Location 是 https；`curl https://...`（不加 -k，即校验证书）访问 `/`、`/training/`、`/ziheng/`、`/farm/`、`/farm/m/`、`/farm/steward/` 的状态码和基线一致；https 下用 qingshan 登录调农场接口返回 code 0；POST 到 http 的接口经 308 后仍是 POST（用 `curl -L` 验证）
- [x] T4 浏览器回归（登录页）
  - 验证：浏览器打开 https://121.40.221.126/farm/ 有锁、不报证书错误，网络面板里接口都是 https、200；实训中心、资衡登录后的页面我没有账号，需要用户或对应负责人确认

## 4. 回滚

```bash
B=/root/backup/https-<时间>
cp $B/hp.conf /etc/nginx/conf.d/hp.conf
rm -f /etc/nginx/conf.d/hp-http.conf
nginx -t && systemctl reload nginx
# 停用自动续期（证书文件留着不影响）：
/root/.acme.sh/acme.sh --uninstall-cronjob
```

回滚后检查：`/`、`/training/`、`/ziheng/`、`/farm/` 的 http 状态码和 T1 基线一致。

## 5. 记录
- T1 [实测 2026-11-05 21:37]：备份 `/root/backup/https-20261105-213701/`（nginx.conf、hp.conf、hp-proxy.inc、farm-locations.inc、SHA256SUMS、baseline.txt）。基线 http：`/` 302，`/training/`、`/ziheng/`、`/farm/`、`/farm/m/`、`/farm/steward/` 200。
- T2 [实测 21:41–21:43]：服务器访问 `github.com/.../archive/...` 超时，改用 `codeload.github.com` 下载 acme.sh 3.1.6 官方包（488 KB）安装；cron `47 5,11,17,23 * * *`。80 端口加 `/.well-known/acme-challenge/`，`nginx -t` 通过后 reload，公网能取到探针文件，基线状态码不变。签发成功：签发者 Let's Encrypt YE1，`IP Address:121.40.221.126`，有效期到 2026-11-12 04:44 GMT（北京时间 12:44）。证书装到 `/etc/nginx/ssl/hp-ip.{crt,key}`，续期后执行 `nginx -t && systemctl reload nginx`。
- T3 [实测 21:43]：hp.conf 改监听 443 ssl（http2、TLS 1.2/1.3），新建 `conf.d/hp-http.conf`（80：证书验证路径 + 其余 308）。`nginx -t` 通过后 reload。公网：`/`、`/training/`、`/ziheng/`、`/farm/`、`/farm/m/?a=1` 都 308 到对应 https 地址（带查询参数）；https 带证书校验（`ssl_verify_result=0`）的状态码和基线一致；http 的 POST 登录接口经 308 后仍是 POST 并返回 token；https 登录后接口返回 code 0；证书验证路径仍走 80。
- T4 [实测]：浏览器打开 http://121.40.221.126/ziheng/#/transfer 自动跳 https，登录页正常、控制台无错误、接口 200。**用户报「这个页面出错了」**：查 nginx 日志，是切换前就打开的 http 页面继续发请求，被 308 到 https 后成了跨域请求，资衡后端不支持预检（OPTIONS 415、资源 401），页面报错；21:45:52 有人在 https 下重新登录后，转账单、验收、设备、盘点、月报、待办等请求全部 200。用户确认「好像可以了」。结论：切换前打开的页面刷新一次即可。农场三个端登录页正常；实训中心登录后的页面我没有账号，没验证。
- 提醒资衡负责人：hp.conf 里资衡的 `proxy_cookie_flags ~ nosecure;` 注释写着「上 HTTPS 后删除」，本次没动。
- 后续：用户要求把这套做成可复用的小程序，新服务器直接部署生成 https 和跳转 → `deploy/https-setup.sh`。另一台服务器 124.223.206.133 也想用，需要单独签发（证书绑定 IP）。
