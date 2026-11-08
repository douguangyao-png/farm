#!/usr/bin/env python3
"""
把 mock-server/seed/*.json 转成真后端（PostgreSQL）的演示数据脚本 backend/sql/postgresql/farm-seed.sql。

初始化顺序（服务器上一次性执行，见 docs/plans/原型对齐与真后端-2026-11-06.md）：
  1. backend/sql/postgresql/ruoyi-vue-pro.sql   芋道自带的系统、基础设施表
  2. backend/sql/postgresql/farm.sql            业务表（会先 DROP 再建，只在初始化时执行）
  3. backend/sql/postgresql/farm-seed.sql       本脚本生成：清掉芋道默认的租户 / 用户 / 角色 / 菜单 / 部门，换成演示数据

种子里的三种特殊写法与 mock-server/src/store.ts 的 convert() 一致：
  - ISO 时间 → 北京时间的 timestamp；
  - "OffsetH:-50" → 执行脚本时刻往前 50 小时（用 SQL 表达式，所以「今日任务」「已逾期」在哪天灌库都成立）；
  - "svg:文字:#颜色" → 带文字的纯色占位图（data URL）。
用法：python deploy/tools/gen-farm-seed.py
"""
import json, re, sys, urllib.parse
from datetime import datetime
from pathlib import Path

import bcrypt

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / 'mock-server' / 'seed'
OUT = ROOT / 'backend' / 'sql' / 'postgresql' / 'farm-seed.sql'
FARM_SQL = (ROOT / 'backend' / 'sql' / 'postgresql' / 'farm.sql').read_text(encoding='utf-8')

ISO = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?([+-]\d{2}:\d{2}|Z)?$')
OFFSET = re.compile(r'^OffsetH:(-?\d+(\.\d+)?)$')


def placeholder_image(label='', color='#4caf50'):
    safe = re.sub(r'[<>&"\']', '', label)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">'
           f'<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{color}"/><stop offset="1" stop-color="{color}" stop-opacity="0.65"/></linearGradient></defs>'
           f'<rect width="400" height="300" fill="url(#g)"/>'
           f'<text x="200" y="160" font-size="34" font-family="sans-serif" fill="#fff" text-anchor="middle">{safe}</text>'
           f'<text x="200" y="200" font-size="14" font-family="sans-serif" fill="#ffffffcc" text-anchor="middle">演示图片</text></svg>')
    return 'data:image/svg+xml;charset=utf-8,' + urllib.parse.quote(svg, safe="~()*!.'")


class Raw(str):
    """原样输出的 SQL 片段"""


def conv(v):
    """递归处理 svg: 写法（时间只在顶层字段处理，JSON 里的时间保持字符串）"""
    if isinstance(v, str):
        if v.startswith('svg:'):
            _, label, color = (v.split(':') + ['', ''])[:3]
            return placeholder_image(label, color)
        return v
    if isinstance(v, list):
        return [conv(x) for x in v]
    if isinstance(v, dict):
        return {k: conv(x) for k, x in v.items()}
    return v


def lit(v, kind=None):
    if isinstance(v, Raw):
        return str(v)
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, str):
        m = OFFSET.match(v)
        if m:
            return f"(CURRENT_TIMESTAMP + interval '{m.group(1)} hours')::timestamp(0)"
        if ISO.match(v):
            d = datetime.fromisoformat(v.replace('Z', '+00:00'))
            return f"'{d.strftime('%Y-%m-%d')}'" if kind == 'date' else f"'{d.strftime('%Y-%m-%d %H:%M:%S')}'"
        return "'" + v.replace("'", "''") + "'"
    return "'" + json.dumps(v, ensure_ascii=False).replace("'", "''") + "'"


def snake(s):
    return re.sub(r'([A-Z])', lambda m: '_' + m.group(1).lower(), s)


def load(name):
    return json.loads((SEED / f'{name}.json').read_text(encoding='utf-8'))


def table_columns():
    cols = {}
    for m in re.finditer(r'CREATE TABLE (farm_\w+) \((.*?)\n\);', FARM_SQL, re.S):
        body = re.sub(r'--.*', '', m.group(2))
        depth, cur, parts = 0, '', []
        for ch in body:
            depth += ch == '('
            depth -= ch == ')'
            if ch == ',' and depth == 0:
                parts.append(cur); cur = ''
            else:
                cur += ch
        parts.append(cur)
        cols[m.group(1)] = {p.split()[0]: p.split()[1] for p in parts if p.strip()}
    return cols


COLS = table_columns()
lines = []
dropped = {}


def insert(table, row, kind_of=None):
    lines.append(f"INSERT INTO {table} ({', '.join(row)}) VALUES ({', '.join(lit(v, (kind_of or {}).get(k)) for k, v in row.items())});")


# ---------- 业务表 ----------
FARM = {
    'bases': 'farm_base', 'assets': 'farm_asset', 'products': 'farm_product', 'members': 'farm_member', 'orders': 'farm_order',
    'adoptions': 'farm_adoption', 'records': 'farm_record', 'tasks': 'farm_task', 'serviceOrders': 'farm_service_order',
    'supplies': 'farm_supply', 'supplyOrders': 'farm_supply_order', 'inputRecords': 'farm_input_record', 'harvests': 'farm_harvest',
    'shipments': 'farm_shipment', 'riskAlerts': 'farm_risk_alert', 'articleCategories': 'farm_article_category',
    'articles': 'farm_article', 'feedbacks': 'farm_feedback',
}
PW_HASH = {}
# 菜单编号加偏移，避开芋道自带按钮权限（type 3）的编号；前端不依赖菜单 id 的具体值
MENU_OFFSET = 100000


def mapmenu(x):
    return x + MENU_OFFSET if x else x



def pw(p):
    if p not in PW_HASH:
        PW_HASH[p] = bcrypt.hashpw(p.encode(), bcrypt.gensalt(10)).decode()
    return PW_HASH[p]


lines.append('-- 由 deploy/tools/gen-farm-seed.py 生成，不要手改；改 mock-server/seed 后重新生成')
lines.append('BEGIN;')

# ---------- 系统表：清掉芋道默认数据，换成演示数据 ----------
for t in ['system_user_role', 'system_role_menu', 'system_users', 'system_role', 'system_dept', 'system_tenant_package', 'system_tenant']:
    lines.append(f'DELETE FROM {t};')
# 菜单只删目录和菜单（type 1、2），保留芋道自带的按钮权限（type 3），经营者管理员按需授予其中一部分
lines.append('DELETE FROM system_menu WHERE type <> 3;')

# ---------- 第一期补齐新增菜单（mock-server/seed/menus.json 不归本脚本管，这里追加；见 docs/plans/第一期补齐-2026-11-08.md 第 6 条） ----------
def _btn(mid, parent, label, perm):
    return {'id': mid, 'parentId': parent, 'name': label, 'permission': perm}


def _page(mid, parent, name, path, comp, cname, icon):
    return {'id': mid, 'parentId': parent, 'name': name, 'path': path, 'component': comp, 'componentName': cname, 'icon': icon}


# (插在哪个菜单 id 之后, 要插入的菜单列表)
EXTRA_MENUS = [
    (321, [_page(322, 310, '发布认养商品', 'product-create', 'farm/product/create', 'FarmProductCreate', 'ep:circle-plus')]),
    (31304, [_page(314, 310, '畜禽批次', 'livestock-batch', 'farm/livestock-batch/index', 'FarmLivestockBatch', 'ep:dish')] + [
        _btn(31400 + i + 1, 314, lbl, f'farm:livestockbatch:{act}')
        for i, (act, lbl) in enumerate([('query', '查询'), ('create', '新增'), ('update', '修改'), ('out', '出栏'), ('delete', '删除')])]),
    (333, [_page(336, 330, '配送批次', 'ship-batch', 'farm/ship-batch/index', 'FarmShipBatch', 'ep:box')] + [
        _btn(33600 + i + 1, 336, lbl, f'farm:shipbatch:{act}')
        for i, (act, lbl) in enumerate([('query', '查询'), ('create', '新建'), ('ship', '发出'), ('finish', '完成')])]),
]

# 第一期已有页面的按钮级权限（type=3），id = 页面菜单 id * 100 + 序号；权限串与前端 v-hasPermi 一致。
# 同时生成增量 SQL backend/sql/postgresql/farm-p1c-perm.sql（已上线的库执行它即可，不必重灌种子）。
_A = {'query': '查询', 'create': '新增', 'update': '修改', 'delete': '删除', 'export': '导出', 'audit': '审核', 'status': '上下架',
      'import': '导入', 'sign': '确认签收', 'print': '打印', 'notify': '通知认养人', 'download': '下载', 'template': '模板管理',
      'trace': '登记施用', 'rule': '派单规则', 'reply': '回复'}
P1C_PAGES = [
    (321, 'product', ['query', 'create', 'update', 'status']),
    (311, 'base', ['query', 'create', 'update', 'delete']),
    (312, 'asset', ['query', 'create', 'update']),
    (331, 'order', ['query', 'create', 'export']),
    (334, 'contract', ['query', 'create', 'download', 'template']),
    (333, 'shipment', ['query', 'create', 'sign', 'export', 'print', 'notify']),
    (332, 'harvest', ['query', 'create']),
    (351, 'supply', ['query', 'create', 'update', 'status', 'import']),
    (352, 'supplyorder', ['query', 'create', 'export', 'trace']),
    (342, 'serviceorder', ['query', 'create', 'rule']),
    (341, 'record', ['query', 'create', 'audit']),
    (206, 'feedback', ['query', 'update', 'reply']),
]
P1C_BTNS = [_btn(pid * 100 + i + 1, pid, _A[a], f'farm:{mod}:{a}')
            for pid, mod, acts in P1C_PAGES for i, a in enumerate(acts)]
for pid, _mod, _acts in P1C_PAGES:
    EXTRA_MENUS.append((pid, [b for b in P1C_BTNS if b['parentId'] == pid]))
EXTRA_IDS = [m['id'] for _, ms in EXTRA_MENUS for m in ms]

packages = load('tenantPackages')
for p in packages:
    p['menuIds'] = list(p['menuIds']) + EXTRA_IDS
for p in packages:
    insert('system_tenant_package', {'id': p['id'], 'name': p['name'], 'status': p['status'], 'remark': p.get('remark', ''),
                                      'menu_ids': json.dumps([mapmenu(i) for i in p['menuIds']]), 'creator': 'admin', 'create_time': p.get('createTime')})
for t in load('tenants'):
    insert('system_tenant', {'id': t['id'], 'name': t['name'], 'contact_name': t.get('contactName', ''), 'contact_mobile': t.get('contactMobile', ''),
                              'status': t['status'], 'websites': ','.join(t.get('websites', [])), 'package_id': t['packageId'],
                              'expire_time': t.get('expireTime'), 'account_count': t.get('accountCount', 10), 'creator': 'admin', 'create_time': t.get('createTime')})

menus = load('menus')
for after, ms in EXTRA_MENUS:
    _pos = next(i for i, m in enumerate(menus) if m['id'] == after)
    menus[_pos + 1:_pos + 1] = ms
for i, m in enumerate(menus):
    insert('system_menu', {'id': mapmenu(m['id']), 'name': m['name'], 'permission': m.get('permission', ''), 'type': 3 if m.get('permission') else (1 if m['parentId'] == 0 else 2), 'sort': i,
                            'parent_id': mapmenu(m['parentId']), 'path': m.get('path', ''), 'icon': m.get('icon', ''), 'component': m.get('component'),
                            'component_name': m.get('componentName'), 'status': 0, 'visible': m.get('visible', True), 'keep_alive': m.get('keepAlive', True),
                            'always_show': m.get('alwaysShow', False), 'creator': 'admin'})

# 文章（芋道商城自带页面的按钮权限：mall 模块没装，这里补上，页面上的 v-hasPermi 才会显示按钮）
_btn_id = 190001
for parent, prefix in ((204, 'promotion:article'), (205, 'promotion:article-category')):
    for act in ('query', 'create', 'update', 'delete'):
        insert('system_menu', {'id': mapmenu(_btn_id), 'name': f'{prefix}:{act}', 'permission': f'{prefix}:{act}', 'type': 3, 'sort': _btn_id,
                                'parent_id': mapmenu(parent), 'path': '', 'icon': '', 'component': None, 'component_name': None, 'status': 0,
                                'visible': True, 'keep_alive': True, 'always_show': False, 'creator': 'admin'})
        _btn_id += 1

# 芋道自带的按钮权限：只留本项目页面用到的，并挂到对应的页面菜单下（芋道会丢掉「父菜单不存在」的按钮，
# 而它原来的父菜单（目录/菜单）已被上面删掉）。短信、OAuth、邮件等无关的按钮直接清掉。
_keep = r'^(system:(user|role|dept|dict|tenant|tenant-package|permission)|system:menu:query)'
lines.append(f"DELETE FROM system_role_menu WHERE menu_id IN (SELECT id FROM system_menu WHERE type = 3 AND id < {MENU_OFFSET} AND permission !~ '{_keep}');")
lines.append(f"DELETE FROM system_menu WHERE type = 3 AND id < {MENU_OFFSET} AND permission !~ '{_keep}';")
lines.append(f"""UPDATE system_menu SET parent_id = CASE
  WHEN permission ~ '^system:user' THEN {mapmenu(201)}
  WHEN permission ~ '^system:(role|permission|menu)' THEN {mapmenu(202)}
  WHEN permission ~ '^system:dict' THEN {mapmenu(203)}
  WHEN permission ~ '^system:dept' THEN {mapmenu(208)}
  WHEN permission ~ '^system:tenant-package' THEN {mapmenu(103)}
  ELSE {mapmenu(102)} END
WHERE type = 3 AND id < {MENU_OFFSET};""")

for d in load('depts'):
    insert('system_dept', {'id': d['id'], 'name': d['name'], 'parent_id': d['parentId'], 'sort': d['sort'], 'status': d['status'],
                            'creator': 'admin', 'tenant_id': d['tenantId']})

rm_id = 1
for r in load('roles'):
    insert('system_role', {'id': r['id'], 'name': r['name'], 'code': r['code'], 'sort': r['id'], 'data_scope': 1, 'status': 0,
                            'type': 1 if r['code'] in ('super_admin', 'tenant_admin') else 2, 'creator': 'admin', 'tenant_id': r['tenantId']})
    ids = [m['id'] for m in menus] if r['menuIds'] == '*' else list(r['menuIds'])
    if r['code'] == 'tenant_admin':
        ids += [i for i in EXTRA_IDS if i not in ids]
    ids = [mapmenu(i) for i in ids]
    for mid in ids:
        insert('system_role_menu', {'id': rm_id, 'role_id': r['id'], 'menu_id': mid, 'creator': 'admin', 'tenant_id': r['tenantId']})
        rm_id += 1

# 经营者管理员（tenant_admin）不是芋道的「超级管理员」，权限校验要靠按钮权限：只授予用户 / 角色 / 部门相关的
lines.append("INSERT INTO system_role_menu (id, role_id, menu_id, creator, tenant_id) "
             "SELECT 200000 + row_number() OVER (), r.id, m.id, 'admin', r.tenant_id FROM system_menu m, system_role r "
             "WHERE r.code = 'tenant_admin' AND m.type = 3 AND m.permission ~ '^(system:user|system:role|system:dept|system:permission|system:menu:query|promotion:article)';")
lines.append("INSERT INTO system_role_menu (id, role_id, menu_id, creator, tenant_id) "
             "SELECT 300000 + row_number() OVER (), r.id, m.id, 'admin', r.tenant_id FROM system_menu m, system_role r "
             "WHERE r.code = 'super_admin' AND m.type = 3;")

ur_id, stewards = 1, []
for u in load('users'):
    insert('system_users', {'id': u['id'], 'username': u['username'], 'password': pw(u['password']), 'nickname': u['nickname'],
                             'dept_id': u.get('deptId'), 'mobile': u.get('mobile', ''), 'sex': 0, 'avatar': u.get('avatar', ''), 'status': 0,
                             'creator': 'admin', 'tenant_id': u['tenantId']})
    for rid in u['roleIds']:
        insert('system_user_role', {'id': ur_id, 'user_id': u['id'], 'role_id': rid, 'creator': 'admin', 'tenant_id': u['tenantId']})
        ur_id += 1
    if u.get('baseId'):
        stewards.append({'id': len(stewards) + 1, 'user_id': u['id'], 'base_id': u['baseId'], 'tenant_id': u['tenantId']})

for s in stewards:
    insert('farm_steward', s)

# ---------- 业务表 ----------
for name, table in FARM.items():
    cols = COLS[table]
    for item in load(name):
        row = {}
        for k, v in item.items():
            c = snake(k)
            if c not in cols:
                dropped.setdefault(f'{name}.{k}', 0)
                dropped[f'{name}.{k}'] += 1
                continue
            row[c] = conv(v)
        if name == 'members' and 'password' in row:
            row['password'] = pw(row['password'])
        if 'creator' in cols and 'creator' not in row:
            row['creator'] = 'seed'
        insert(table, row, {c: 'date' for c, t in cols.items() if t.startswith('date')})

# ---------- 序列：从 max(id)+1 或 1000 起，避免新增数据撞主键 ----------
seqs = [t for t in COLS] + ['system_tenant', 'system_tenant_package', 'system_users', 'system_role', 'system_menu', 'system_dept', 'system_role_menu', 'system_user_role']
for t in seqs:
    lines.append(f"SELECT setval('{t}_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM {t}) + 1, 1000), false);")
lines.append('COMMIT;')

OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')

# ---------- 增量 SQL：第一期按钮权限（可重复执行，只 INSERT / 追加，不删数据） ----------
_ids = [b['id'] + MENU_OFFSET for b in P1C_BTNS]
_arr = 'ARRAY[' + ', '.join(str(i) for i in _ids) + ']::bigint[]'
inc = ['-- 由 deploy/tools/gen-farm-seed.py 生成，不要手改。第一期页面按钮级权限（type=3）的增量脚本，可重复执行：',
       '--   1) 新增按钮菜单（已存在的 id 跳过）；2) 授予所有 tenant_admin 角色（已有的跳过）；3) 追加进所有租户套餐的 menu_ids（已含的跳过）。',
       f'-- 共 {len(_ids)} 个按钮，菜单 id {min(_ids)}~{max(_ids)}（含 +{MENU_OFFSET} 偏移）。', 'BEGIN;']
for b in P1C_BTNS:
    inc.append("INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator) "
               f"VALUES ({mapmenu(b['id'])}, {lit(b['name'])}, {lit(b['permission'])}, 3, {b['id'] % 100}, {mapmenu(b['parentId'])}, '', '', NULL, NULL, 0, true, true, false, 'admin') "
               "ON CONFLICT (id) DO NOTHING;")
inc.append(f"""INSERT INTO system_role_menu (id, role_id, menu_id, creator, tenant_id)
SELECT nextval('system_role_menu_seq'), r.id, m.id, 'admin', r.tenant_id
FROM system_role r CROSS JOIN unnest({_arr}) AS m(id)
WHERE r.code = 'tenant_admin' AND r.deleted = 0
  AND EXISTS (SELECT 1 FROM system_menu x WHERE x.id = m.id)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu e WHERE e.role_id = r.id AND e.menu_id = m.id AND e.deleted = 0);""")
inc.append(f"""UPDATE system_tenant_package p
SET menu_ids = (p.menu_ids::jsonb || (SELECT COALESCE(jsonb_agg(i), '[]'::jsonb) FROM unnest({_arr}) AS i WHERE NOT (p.menu_ids::jsonb @> to_jsonb(i))))::text
WHERE p.deleted = 0;""")
inc.append("SELECT setval('system_role_menu_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM system_role_menu) + 1, nextval('system_role_menu_seq')), false);")
inc.append('COMMIT;')
INC_OUT = OUT.with_name('farm-p1c-perm.sql')
INC_OUT.write_text(chr(10).join(inc) + chr(10), encoding='utf-8', newline=chr(10))
print(f'写出 {INC_OUT}，{len(P1C_BTNS)} 个按钮')

print(f'写出 {OUT}，{len(lines)} 行；密码哈希 {len(PW_HASH)} 种')
if dropped:
    print('种子里有、但表里没有的字段（已丢弃）：', dropped)
