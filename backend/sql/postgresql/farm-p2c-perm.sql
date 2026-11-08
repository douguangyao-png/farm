-- 由 deploy/tools/gen-farm-seed.py 生成，不要手改。第二期页面按钮级权限（type=3）的增量脚本，可重复执行：
--   1) 新增按钮菜单（已存在的 id 跳过）；2) 授予所有 tenant_admin 角色（已有的跳过）；3) 追加进所有租户套餐的 menu_ids（已含的跳过）。
-- 共 7 个按钮，菜单 id 120905~136305（含 +100000 偏移）。
BEGIN;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) VALUES (133504, '联系客户', 'farm:refund:contact', 3, 4, 100335, '', '', NULL, NULL, 0, true, true, false, 'admin') ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) VALUES (136104, '新增', 'farm:customer:create', 3, 4, 100361, '', '', NULL, NULL, 0, true, true, false, 'admin') ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) VALUES (136105, '发消息', 'farm:customer:message', 3, 5, 100361, '', '', NULL, NULL, 0, true, true, false, 'admin') ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) VALUES (136106, '关怀', 'farm:customer:care', 3, 6, 100361, '', '', NULL, NULL, 0, true, true, false, 'admin') ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) VALUES (136205, '推送', 'farm:customertier:push', 3, 5, 100362, '', '', NULL, NULL, 0, true, true, false, 'admin') ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) VALUES (136305, '派单', 'farm:stewardmanage:dispatch', 3, 5, 100363, '', '', NULL, NULL, 0, true, true, false, 'admin') ON CONFLICT (id) DO NOTHING;
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) VALUES (120905, '测试发送', 'farm:msgtemplate:send', 3, 5, 100209, '', '', NULL, NULL, 0, true, true, false, 'admin') ON CONFLICT (id) DO NOTHING;
INSERT INTO system_role_menu (id, role_id, menu_id, creator, tenant_id)
SELECT nextval('system_role_menu_seq'), r.id, m.id, 'admin', r.tenant_id
FROM system_role r CROSS JOIN unnest(ARRAY[133504, 136104, 136105, 136106, 136205, 136305, 120905]::bigint[]) AS m(id)
WHERE r.code = 'tenant_admin' AND r.deleted = 0
  AND EXISTS (SELECT 1 FROM system_menu x WHERE x.id = m.id)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu e WHERE e.role_id = r.id AND e.menu_id = m.id AND e.deleted = 0);
UPDATE system_tenant_package p
SET menu_ids = (p.menu_ids::jsonb || (SELECT COALESCE(jsonb_agg(i), '[]'::jsonb) FROM unnest(ARRAY[133504, 136104, 136105, 136106, 136205, 136305, 120905]::bigint[]) AS i WHERE NOT (p.menu_ids::jsonb @> to_jsonb(i))))::text
WHERE p.deleted = 0;
SELECT setval('system_role_menu_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM system_role_menu) + 1, nextval('system_role_menu_seq')), false);
COMMIT;
