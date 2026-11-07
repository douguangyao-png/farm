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


def pw(p):
    if p not in PW_HASH:
        PW_HASH[p] = bcrypt.hashpw(p.encode(), bcrypt.gensalt(10)).decode()
    return PW_HASH[p]


lines.append('-- 由 deploy/tools/gen-farm-seed.py 生成，不要手改；改 mock-server/seed 后重新生成')
lines.append('BEGIN;')

# ---------- 系统表：清掉芋道默认数据，换成演示数据 ----------
for t in ['system_user_role', 'system_role_menu', 'system_users', 'system_role', 'system_menu', 'system_dept', 'system_tenant_package', 'system_tenant']:
    lines.append(f'DELETE FROM {t};')

packages = load('tenantPackages')
for p in packages:
    insert('system_tenant_package', {'id': p['id'], 'name': p['name'], 'status': p['status'], 'remark': p.get('remark', ''),
                                      'menu_ids': json.dumps(p['menuIds']), 'creator': 'admin', 'create_time': p.get('createTime')})
for t in load('tenants'):
    insert('system_tenant', {'id': t['id'], 'name': t['name'], 'contact_name': t.get('contactName', ''), 'contact_mobile': t.get('contactMobile', ''),
                              'status': t['status'], 'websites': ','.join(t.get('websites', [])), 'package_id': t['packageId'],
                              'expire_time': t.get('expireTime'), 'account_count': t.get('accountCount', 10), 'creator': 'admin', 'create_time': t.get('createTime')})

menus = load('menus')
for i, m in enumerate(menus):
    insert('system_menu', {'id': m['id'], 'name': m['name'], 'permission': m.get('permission', ''), 'type': 1 if m['parentId'] == 0 else 2, 'sort': i,
                            'parent_id': m['parentId'], 'path': m.get('path', ''), 'icon': m.get('icon', ''), 'component': m.get('component'),
                            'component_name': m.get('componentName'), 'status': 0, 'visible': m.get('visible', True), 'keep_alive': m.get('keepAlive', True),
                            'always_show': m.get('alwaysShow', False), 'creator': 'admin'})

for d in load('depts'):
    insert('system_dept', {'id': d['id'], 'name': d['name'], 'parent_id': d['parentId'], 'sort': d['sort'], 'status': d['status'],
                            'creator': 'admin', 'tenant_id': d['tenantId']})

rm_id = 1
for r in load('roles'):
    insert('system_role', {'id': r['id'], 'name': r['name'], 'code': r['code'], 'sort': r['id'], 'data_scope': 1, 'status': 0,
                            'type': 1 if r['code'] in ('super_admin', 'tenant_admin') else 2, 'creator': 'admin', 'tenant_id': r['tenantId']})
    ids = [m['id'] for m in menus] if r['menuIds'] == '*' else r['menuIds']
    for mid in ids:
        insert('system_role_menu', {'id': rm_id, 'role_id': r['id'], 'menu_id': mid, 'creator': 'admin', 'tenant_id': r['tenantId']})
        rm_id += 1

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
print(f'写出 {OUT}，{len(lines)} 行；密码哈希 {len(PW_HASH)} 种')
if dropped:
    print('种子里有、但表里没有的字段（已丢弃）：', dropped)
