# 认养农场 SaaS 演示 Demo（farmer-saas/docs/plans/deploy-演示环境-121.40.221.126-2026-11-05.md）
# 由 /etc/nginx/conf.d/hp.conf 里的一行 include 引入；回滚：删掉那一行 include 和本文件
#
# 不设访问密码（用户 2026-11-05：不要浏览器弹出的登录框）。服务器上只有 mock 演示数据。
# 如果以后要加访问控制，注意两点（2026-11-05 实测）：
# - /farm-api/ 不能用 Basic Auth：前端登录后每个请求都带 Authorization: Bearer，会顶掉 Basic 头，登录后接口全部 401；
# - 用 Cookie 方案时 add_header 不能加 always，否则 401 响应也会下发 Cookie，等于绕过密码。

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
location /farm-api/__mock/ { return 404; }      # 重置、未实现接口清单等调试入口不对外
location /farm-api/ {
    proxy_pass http://127.0.0.1:48090/;          # 末尾斜杠：去掉 /farm-api 前缀再转发
    include /etc/nginx/hp-proxy.inc;
    client_max_body_size 20m;                    # 反馈截图、打卡照片
}

# ---- 真后端预览（2026-11-07，docs/plans/原型对齐与真后端-2026-11-06.md）：和上面的 mock 演示并存，互不影响 ----
# 前端：/farm-real/ → /opt/farmer-saas/web/admin-real/；接口：/farm-real-api/ → 芋道 + farm 模块（127.0.0.1:48080）
# 回滚：删掉本段，nginx -t && systemctl reload nginx（/etc/nginx/farm-locations.inc.bak-* 是上一版）
location = /farm-real { return 301 /farm-real/; }
location /farm-real/m/ {
    alias /opt/farmer-saas/web/m-real/;
    try_files $uri $uri/ /farm-real/m/index.html;
}
location /farm-real/steward/ {
    alias /opt/farmer-saas/web/steward-real/;
    try_files $uri $uri/ /farm-real/steward/index.html;
}
location /farm-real/ {
    alias /opt/farmer-saas/web/admin-real/;
    try_files $uri $uri/ /farm-real/index.html;
}
# 只放行业务接口：芋道自带的 actuator、接口文档、druid 控制台、websocket 等调试入口一律 404（2026-11-07 实测它们经 nginx 对外可达）
location ~ ^/farm-real-api/(?!(admin-api|app-api)/) { return 404; }
location /farm-real-api/ {
    proxy_pass http://127.0.0.1:48080/;          # 末尾斜杠：去掉 /farm-real-api 前缀再转发
    include /etc/nginx/hp-proxy.inc;
    client_max_body_size 20m;
}
