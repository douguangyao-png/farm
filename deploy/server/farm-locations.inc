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
