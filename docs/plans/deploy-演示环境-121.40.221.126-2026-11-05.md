# 部署四端演示 Demo 到 121.40.221.126（/farm/ 路径）

- 类型：新功能
- 档位：B
- 风险：高（共享生产服务器，上面跑着实训中心和资衡；要改 nginx 全局配置并 reload，出错会让这两个系统一起不可用）
- 状态：执行中
- 批准：用户 2026-11-05
- 关联：docs/plans/demo-四端演示Demo-2026-11-04.md；服务器上 /etc/nginx/conf.d/hp.conf 头部注释写的「新服务器迁移方案 3.6」

## 1. 调研

- **目标：** 把 mock 模式的四端 Demo 发布到 `http://121.40.221.126/farm/` 下，客户用浏览器就能访问。服务器上已有的服务一个都不能受影响。
- **影响面：** nginx（80 端口，唯一的 server 块，在 `hp.conf` 里）上的所有站点：`/training/`（实训中心，127.0.0.1:8095）、`/ziheng/`（资衡评审站，127.0.0.1:18096）、`/` 跳转到 `/training/`。清单来自 `cat /etc/nginx/conf.d/hp.conf`、`ss -ltnp`、`systemctl list-units --state=running`。
- **同类：** 服务器上的应用都是「systemd 服务 + 只监听 127.0.0.1 + nginx 按路径反代」，比如 training.service、ziheng-biz.service、ziheng-review.service。这次照同一套路做。
- **改法：** 在 `hp.conf` 的 `location / { return 404; }` 前面加一行 `include /etc/nginx/farm-locations.inc;`。`/farm/` 相关的配置全部放进这个新文件。mock 服务用独立的 Node 运行时（放在 `/opt/farmer-saas/node`，不用 dnf 装系统包），由 systemd 管理，只监听 127.0.0.1:48090。

证据（2026-11-05，只读查看）：
- [实测] Rocky Linux 9.8，nginx 1.26.3，conf.d 里只有 `hp.conf`；`hp.conf` 只有一个 `default_server`，最后是 `location / { return 404; }`。
- [实测] 正在监听的端口：80（nginx）、22、5432（PostgreSQL，仅本机）、8095、18096、18097（Java，仅本机）、8888（宝塔面板，对公网）。48090 没被占用。
- [实测] 服务器上没装 Node；SELinux 是 Disabled；firewalld 放行 80，不需要改防火墙。
- [实测] 磁盘 100G，已用 6.4G；内存 14G，可用 12G。
- [实测] 本机 `~/.ssh/id_ed25519` 可以免密登录 root。
- [实测] `/root/readme.txt` 里有明文的宝塔面板密码（只读查看时被打印出来），建议用户改密码并删掉这个文件。不在本方案范围内。

## 2. 设计

**服务器上的目录**（新建，不动已有目录）：

```
/opt/farmer-saas/
  node/            Node 22 LTS 官方 Linux x64 二进制包解压，只给 mock 服务用
  mock-server/     打包后的 mock 服务（单个 JS 文件 + seed/）
  web/admin/       后台静态文件（构建时 base=/farm/）
  web/m/           C 端 H5（base=/farm/m/）
  web/steward/     管家端 H5（base=/farm/steward/）
  releases/        每次发布的压缩包，按时间命名，回滚时用
/root/backup/farm-deploy-<时间>/   改 nginx 之前的配置备份
```

**mock 服务**：新建系统用户 `farm`（没有登录 shell），用 `farm-mock.service` 运行，`Restart=always`，监听 `127.0.0.1:48090`，不对公网开放。

**nginx**：`hp.conf` 只加一行 include，带注释说明出处。新文件 `/etc/nginx/farm-locations.inc` 的内容：

```nginx
# 认养农场 SaaS 演示 Demo（farmer-saas/docs/plans/deploy-演示环境-121.40.221.126-2026-11-05.md）
location = /farm { return 301 /farm/; }
location /farm/m/ {
    alias /opt/farmer-saas/web/m/;
    try_files $uri $uri/ /farm/m/index.html;
}
location /farm/steward/ {
    alias /opt/farmer-saas/web/steward/;
    try_files $uri $uri/ /farm/steward/index.html;
}
location /farm/ {
    alias /opt/farmer-saas/web/admin/;
    try_files $uri $uri/ /farm/index.html;
}
location /farm-api/ {
    proxy_pass http://127.0.0.1:48090/;      # 末尾斜杠：去掉 /farm-api 前缀再转发
    include /etc/nginx/hp-proxy.inc;
    client_max_body_size 20m;                # 反馈截图、打卡照片
}
```

如果用户同意加访问密码，就在上面四个 location 里加 `auth_basic` 和 `auth_basic_user_file /etc/nginx/farm.htpasswd`。`/farm-api/` 也要加，否则绕过页面直接调接口就能拿到数据。

**前端构建**：每个前端再加一个 `demo` 模式。它和 `mock` 模式的区别只有两点：路径前缀改成上面的子路径；接口地址改成同源的 `/farm-api`。C 端的租户识别要把 `121.40.221.126` 加进 seed 的 websites。

**否决的方案**：
- 直接改 `hp.conf` 写进所有 location：会把我们的配置混进别的项目的文件里，回滚时容易误伤。所以只加一行 include。
- 新开一个端口（比如 8090）单独起 server：要改防火墙和阿里云安全组，客户访问还得记端口。
- 用 dnf 装系统 Node：会影响整台机器的环境，以后别的项目也可能被它牵连。

## 3. 任务

- [x] T1 本机：三个前端加 `demo` 构建模式，mock 服务打包成单文件；构建产物检查（需求：见关联方案 R-7）
  - 验证：三个产物里 grep 不到 `localhost:48090`，HTML 里的资源路径以 `/farm/`、`/farm/m/`、`/farm/steward/` 开头；本机用 `npx serve` 模拟子路径打开能登录
- [x] T2 服务器：备份 nginx 配置，记录部署前的基线
  - 验证：`/root/backup/farm-deploy-<时间>/` 里有 `nginx.conf`、`conf.d/hp.conf`、`hp-proxy.inc` 和 sha256 清单；基线是 `curl -s -o /dev/null -w "%{http_code}"` 访问 `/`、`/training/`、`/ziheng/` 的状态码，写进「记录」
- [x] T3 服务器：建 `farm` 用户和 `/opt/farmer-saas`，上传 Node 和 mock 服务，配置并启动 `farm-mock.service`
  - 验证：`systemctl is-active farm-mock` 输出 `active`；`curl 127.0.0.1:48090/__mock/health` 返回 `"up"`；`ss -ltnp` 显示 48090 只绑在 127.0.0.1
- [x] T4 服务器：上传三个前端；写 `farm-locations.inc`；`hp.conf` 加一行 include；`nginx -t` 通过后再 `systemctl reload nginx`
  - 验证：`nginx -t` 输出 `syntax is ok` 和 `test is successful`；reload 后 `/`、`/training/`、`/ziheng/` 的状态码和基线完全一致；`/farm/` 返回 200
- [ ] T5 部署后回归（全局规则：必须走到登录后的页面）
  - 验证：在浏览器里打开三个端，分别用演示账号登录进首页；网络面板里能看到请求发到 `/farm-api/...` 并返回 200；`/training/` 和 `/ziheng/` 能正常打开登录页（这两个系统登录后的页面我没有账号验证，需要用户自己点一下）

## 4. 回滚

T4 之后任何一步出问题，按下面执行：

```bash
B=/root/backup/farm-deploy-<时间>
cp $B/conf.d/hp.conf /etc/nginx/conf.d/hp.conf
rm -f /etc/nginx/farm-locations.inc /etc/nginx/farm.htpasswd /etc/nginx/farm-secret.inc
nginx -t && systemctl reload nginx
systemctl disable --now farm-mock && rm -f /etc/systemd/system/farm-mock.service && systemctl daemon-reload
# 可选，彻底清理：rm -rf /opt/farmer-saas && userdel farm
```

回滚后检查：`/`、`/training/`、`/ziheng/` 的状态码和 T2 记录的基线一致。

前端发错版本（nginx 没问题）时，只回滚前端：从 `/opt/farmer-saas/releases/` 解压上一个包覆盖 `web/`，不用 reload nginx。

## 5. 记录

- 2026-11-05 用户确认：①方案批准；②加访问密码（Basic Auth）；③现在就部署（T1 骨架阶段），以后每完成一个 Demo 任务更新一次；④问题反馈只做后台；⑤宝塔密码文件用户自己处理，本方案不管。
- 2026-11-05 用户补充：以后所有系统（实训中心、资衡、农场）各用一个端口，「现在先不改」。本次仍按 /farm/ 走 80 端口；以后迁到独立端口时，农场三端改回根路径构建、单独一个 server 块即可，不再需要 hp.conf 的 include。阿里云安全组正在开 443、8080~8090（入方向），本次部署用不到。
- 访问密码：用户名 demo，密码随机生成，只存在服务器 `/root/farm-demo-basic-auth.txt`（600 权限），不写进仓库和对话。
- 浏览器验证的限制：演示账号和 Basic Auth 都要在浏览器里输密码，而目标是公网 IP，不属于我可以代输密码的本机/测试域名，所以登录后的页面要用户自己在浏览器里走一遍。我能做的是在服务器本机用 curl 走完整链路（经 nginx、带 Basic Auth、登录拿 token、带 token 调登录后的接口）。
- T2 [实测 2026-11-05 12:05]：备份在 `/root/backup/farm-deploy-20261105-120518/`（nginx.conf、conf.d/hp.conf、hp-proxy.inc、SHA256SUMS、baseline.txt）。基线（服务器本机 curl 127.0.0.1）：`/` 302，`/training/` 200，`/ziheng/` 200，`/farm/` 404，`/farm-api/` 404。
- T3 [实测 2026-11-05]：Node v22.23.4（nodejs.org 官方包，SHASUMS256 校验 OK）解压到 `/opt/farmer-saas/node`；系统用户 farm（nologin）；`systemctl is-active farm-mock` = active；`/__mock/health` 返回 `{"code":0,"data":"up"}`；`ss -ltnp` 显示 `127.0.0.1:48090`。服务文件和 nginx 片段放在仓库 `deploy/server/`。
- 设计补充：`/farm-api/__mock/` 在 nginx 层返回 404，重置数据、未实现接口清单这些调试入口不对公网开放。
- T1 [实测 2026-11-05]：三个前端加 `demo` 模式（后台 `.env.demo` / `pnpm build:demo`；C 端 `.env.demo` / `npm run build:h5:demo`，子路径由 `scripts/uni.cjs` 构建时临时改 manifest 的 h5.router.base、退出时还原，构建后 `git diff manifest.json` 为空；管家端 `env/.env.demo` / `pnpm build:h5:demo`）。mock 服务 `npm run bundle` 打成 `dist/server.mjs`（1.1MB），监听地址默认改为 127.0.0.1；测试 13/13 通过。产物检查：三个 index.html 的资源路径分别以 `/farm/`、`/farm/m/`、`/farm/steward/` 开头；三个产物都 grep 不到 `localhost:48090`；C 端、管家端 `routerBase` 分别是 `/farm/m/`、`/farm/steward/`。本机用 express 模拟 nginx 路径（127.0.0.1:8099），浏览器里：后台 qingshan 登录进 `/farm/index` 首页，管家端 zhangguanjia 登录进工作台，C 端首页渲染（弹出短信登录框）；网络面板里请求都是 `/farm-api/admin-api/...`、`/farm-api/app-api/...`，返回 200。
- T4 [实测 2026-11-05]：`nginx -t` 输出 syntax is ok / test is successful，reload 后 `/` 302、`/training/` 200、`/ziheng/` 200，和基线一致。hp.conf 只多了两行（一行注释 + include），`diff` 已核对。
- **T4 中发现并修正的问题** [实测]：最初 `/farm-api/` 也用 Basic Auth，但前端登录后每个请求都带 `Authorization: Bearer`，会顶掉 Basic 头，带 token 的接口全部被 nginx 401——也就是登录页能过、登录后全挂。改为：页面路径 Basic Auth 通过后下发 HttpOnly Cookie `farm_demo`（随机值在服务器 `/etc/nginx/farm-secret.inc`，不进仓库），`/farm-api/` 校验这个 Cookie。改前的版本备份为 `$B/farm-locations.inc.v1`。改后服务器本机 curl：不带/伪造 Cookie 调接口 401；Basic Auth 拿到 Cookie 后，qingshan、zhangguanjia 登录并调 `get-permission-info` 返回 code 0，会员 13800000001 登录并调 `member/user/get` 返回周女士；`/farm-api/__mock/*` 返回 404；从公网不带密码访问 `/farm/` 401。
- 回滚补充：第 4 节的 `rm -f` 还要加上 `/etc/nginx/farm-secret.inc`。
- T5 未完成：**公网浏览器里登录后的页面我没验证**（要在浏览器里输 Basic Auth 和演示账号密码，目标是公网 IP，我不能代输），需要用户按演示手册走一遍三个端。
- **安全问题** [实测 2026-11-05]：Cookie 方案里 `add_header ... always` 让不带密码的 401 响应也下发了 Cookie，用它可以直接调接口（公网实测返回 200）。已去掉 always 并换了随机值，复测 401 不再下发 Cookie。备份 `$B/farm-locations.inc.v2`、`$B/farm-secret.inc.v1`。
- **用户改口径（2026-11-05）**：「为什么有一个弹出来的登录框，不要这个」→ 去掉访问密码。nginx 片段只剩 alias 和反代；已删除服务器上的 `farm.htpasswd`、`farm-secret.inc`、`/root/farm-demo-basic-auth.txt`。改前版本备份 `$B/farm-locations.inc.v3`。现状：`/farm/` 等页面和 `/farm-api/` 公网可直接访问，只有 mock 演示数据；`/farm-api/__mock/*` 仍返回 404。
- 复测 [实测]：公网 `/` 302、`/training/` 200、`/ziheng/` 200（与基线一致）；`/farm/`、`/farm/index`、`/farm/m/`、`/farm/steward/` 200；公网 curl 用 qingshan 登录并调 `get-permission-info` 返回 code 0；浏览器打开 `http://121.40.221.126/farm/` 直接显示系统登录页，网络面板 `/farm-api/admin-api/system/tenant/get-by-website?website=121.40.221.126` 200，控制台无错误。
- T5 仍未完成：公网浏览器里登录后的页面我没验证（不在本机/测试域名上代输密码、代点登录），需要用户走一遍三个端。
