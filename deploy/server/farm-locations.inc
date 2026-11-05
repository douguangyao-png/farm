# 认养农场 SaaS 演示 Demo（farmer-saas/docs/plans/deploy-演示环境-121.40.221.126-2026-11-05.md）
# 由 /etc/nginx/conf.d/hp.conf 里的一行 include 引入；回滚：删掉那一行 include 和本文件
#
# 访问控制：
# - 页面路径用 Basic Auth（浏览器弹窗输 demo 账号）；通过后下发 Cookie farm_demo。
# - /farm-api/ 不能用 Basic Auth：前端登录后每个请求都带 Authorization: Bearer <token>，
#   会顶掉浏览器的 Basic 头，nginx 就全部 401（2026-11-05 实测）。所以接口改为校验 Cookie。
# - Cookie 的值在 /etc/nginx/farm-secret.inc（服务器上随机生成，不进仓库），内容形如：
#     set $farm_token "<32 位随机十六进制>";

location = /farm { return 301 /farm/; }

location /farm/m/ {
    auth_basic "farm demo";
    auth_basic_user_file /etc/nginx/farm.htpasswd;
    include /etc/nginx/farm-secret.inc;
    add_header Set-Cookie "farm_demo=$farm_token; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000" always;
    alias /opt/farmer-saas/web/m/;
    try_files $uri $uri/ /farm/m/index.html;
}
location /farm/steward/ {
    auth_basic "farm demo";
    auth_basic_user_file /etc/nginx/farm.htpasswd;
    include /etc/nginx/farm-secret.inc;
    add_header Set-Cookie "farm_demo=$farm_token; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000" always;
    alias /opt/farmer-saas/web/steward/;
    try_files $uri $uri/ /farm/steward/index.html;
}
location /farm/ {
    auth_basic "farm demo";
    auth_basic_user_file /etc/nginx/farm.htpasswd;
    include /etc/nginx/farm-secret.inc;
    add_header Set-Cookie "farm_demo=$farm_token; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000" always;
    alias /opt/farmer-saas/web/admin/;
    try_files $uri $uri/ /farm/index.html;
}
location /farm-api/__mock/ { return 404; }      # 重置、未实现接口清单等调试入口不对外
location /farm-api/ {
    include /etc/nginx/farm-secret.inc;
    if ($cookie_farm_demo != $farm_token) { return 401; }
    proxy_pass http://127.0.0.1:48090/;          # 末尾斜杠：去掉 /farm-api 前缀再转发
    include /etc/nginx/hp-proxy.inc;
    client_max_body_size 20m;                    # 反馈截图、打卡照片
}
