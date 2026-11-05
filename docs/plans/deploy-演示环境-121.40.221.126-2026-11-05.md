# 部署四端演示 Demo 到 121.40.221.126（/farm/ 路径）

- 类型：新功能
- 档位：B
- 风险：高（共享生产服务器，上面跑着实训中心和资衡；要改 nginx 全局配置并 reload，出错会让这两个系统一起不可用）
- 状态：草稿
- 批准：待批准
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

- [ ] T1 本机：三个前端加 `demo` 构建模式，mock 服务打包成单文件；构建产物检查（需求：见关联方案 R-7）
  - 验证：三个产物里 grep 不到 `localhost:48090`，HTML 里的资源路径以 `/farm/`、`/farm/m/`、`/farm/steward/` 开头；本机用 `npx serve` 模拟子路径打开能登录
- [ ] T2 服务器：备份 nginx 配置，记录部署前的基线
  - 验证：`/root/backup/farm-deploy-<时间>/` 里有 `nginx.conf`、`conf.d/hp.conf`、`hp-proxy.inc` 和 sha256 清单；基线是 `curl -s -o /dev/null -w "%{http_code}"` 访问 `/`、`/training/`、`/ziheng/` 的状态码，写进「记录」
- [ ] T3 服务器：建 `farm` 用户和 `/opt/farmer-saas`，上传 Node 和 mock 服务，配置并启动 `farm-mock.service`
  - 验证：`systemctl is-active farm-mock` 输出 `active`；`curl 127.0.0.1:48090/__mock/health` 返回 `"up"`；`ss -ltnp` 显示 48090 只绑在 127.0.0.1
- [ ] T4 服务器：上传三个前端；写 `farm-locations.inc`；`hp.conf` 加一行 include；`nginx -t` 通过后再 `systemctl reload nginx`
  - 验证：`nginx -t` 输出 `syntax is ok` 和 `test is successful`；reload 后 `/`、`/training/`、`/ziheng/` 的状态码和基线完全一致；`/farm/` 返回 200
- [ ] T5 部署后回归（全局规则：必须走到登录后的页面）
  - 验证：在浏览器里打开三个端，分别用演示账号登录进首页；网络面板里能看到请求发到 `/farm-api/...` 并返回 200；`/training/` 和 `/ziheng/` 能正常打开登录页（这两个系统登录后的页面我没有账号验证，需要用户自己点一下）

## 4. 回滚

T4 之后任何一步出问题，按下面执行：

```bash
B=/root/backup/farm-deploy-<时间>
cp $B/conf.d/hp.conf /etc/nginx/conf.d/hp.conf
rm -f /etc/nginx/farm-locations.inc /etc/nginx/farm.htpasswd
nginx -t && systemctl reload nginx
systemctl disable --now farm-mock && rm -f /etc/systemd/system/farm-mock.service && systemctl daemon-reload
# 可选，彻底清理：rm -rf /opt/farmer-saas && userdel farm
```

回滚后检查：`/`、`/training/`、`/ziheng/` 的状态码和 T2 记录的基线一致。

前端发错版本（nginx 没问题）时，只回滚前端：从 `/opt/farmer-saas/releases/` 解压上一个包覆盖 `web/`，不用 reload nginx。

## 5. 记录

- 待用户确认：①本方案；②要不要加访问密码（Basic Auth）；③现在就部署（目前只有 T1 骨架，后台还是芋道自带的页面），还是等 Demo 做完再部署。
