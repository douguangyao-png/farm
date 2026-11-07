# 第二期登记：A（退款闭环、客户管理、客户分层、管家管理）

状态：代码已写完，**未在服务器跑过任何类型检查/测试/构建**。本机只做了一次 mock-server 的 `tsc --noEmit`，无报错 [实测]；Java、Vue 只做了目测 [未验证]。登录后的页面没有验证。

## 0. 我新增的文件（没有改任何共享文件）

| 层 | 文件 |
|---|---|
| mock 路由 | `mock-server/src/routes/farm-refund.ts`、`mock-server/src/routes/farm-people.ts` |
| mock 种子 | `mock-server/seed/refunds.json`（新集合 `refunds`，1 条已驳回的演示退款；`loadSeed` 会按文件名自动载入） |
| Java | `enums/FarmRefundErrorCodes.java`、`dal/dataobject/FarmRefundDO.java`、`dal/mysql/FarmRefundMapper.java`、`service/FarmRefundService.java`、`service/FarmCustomerService.java`、`service/FarmStewardManageService.java`、`controller/admin/refund/**`、`controller/app/refund/**`、`controller/admin/customer/**`、`controller/admin/stewardmanage/**`（包路径前缀 `backend/yudao-module-farm/src/main/java/cn/iocoder/yudao/module/farm/`） |
| web-admin | `views/farm/refund/{index.vue,constants.ts}`、`views/farm/customer/{index.vue,constants.ts}`、`views/farm/customer-tier/{index.vue,constants.ts}`、`views/farm/steward-manage/{index.vue,constants.ts}`、`api/farm/{refund,customer,stewardManage}.ts` |
| 文档 | 本文件；`docs/plans/第二期-权限清单.md` 的「A」小节 |

## 1. app.ts 里要加的 import / 注册行

import（和其他 router 放一起）：

```ts
import { farmRefundRouter, farmAppRefundRouter } from './routes/farm-refund.js'
import { farmCustomerRouter, farmCustomerTierRouter, farmStewardManageRouter } from './routes/farm-people.js'
```

注册（**必须放在 `app.use('/admin-api/farm', farmAdminRouter)` 和 `app.use('/app-api/farm', farmAppRouter)` 之前**；`farm-extra` 等其他 agent 的行不冲突）：

```ts
  app.use('/admin-api/farm/refund', farmRefundRouter)
  app.use('/admin-api/farm/customer-tier', farmCustomerTierRouter)
  app.use('/admin-api/farm/customer', farmCustomerRouter)
  app.use('/admin-api/farm/steward-manage', farmStewardManageRouter)   // 注意不是 /steward，那是管家端
  app.use('/app-api/farm/refund', farmAppRefundRouter)
```

## 2. 必须由主线程跟进的共享文件改动（我不能改，不改则联动不完整）

1. `mock-server/src/farm/views.ts` 的 `contractStatusOf`：订单状态 50（已退款）要显示合同「已解除」。把 `if (o.status === 30) return 20` 改为 `if (o.status === 30 || o.status === 50) return 20`。Java 同步改 `FarmContractService.statusOf`（`== 30` 改为 `== 30 || == 50`）以及它 `page()` 里按状态筛选的 `FarmOrderDO::getStatus, 30` 分支（20 已解除要包含 50）。
2. `web-admin/src/views/farm/constants.ts` 的 `ORDER_STATUS` 加两项：`{ value: 40, label: '退款中', type: 'danger' }`、`{ value: 50, label: '已退款', type: 'info' }`（订单页签「退款中」已经在 `order/constants.ts` 里，列表和详情的状态标签靠这个枚举）。C 端订单状态文案同理加这两项。
3. 工作台：`dashboard/summary` 的 `todos` 可加「退款待审核」，mock 里可直接调 `pendingRefundCount(tenantId)`（`farm-refund.ts` 已导出）；`weekOverview.refundRate` 现在恒为 null，可改用退款单数 / 已付订单数。Java 对应用 `FarmRefundService.stats()` 的 `refundRate`。
4. `docs/api/farm-接口契约.md` 的枚举表：订单状态加 `40` 退款中、`50` 已退款；认养状态 `adoption.status` 加 `30` 已终止；任务状态加 `30` 已关闭；新增接口见第 4 节。
5. mock 的 `Db` 类型（store.ts）没有 `refunds` 字段；我在 `farm-refund.ts` 里用 `(getDb() as {refunds?}).refunds ??= []` 访问，resetDb 会重新载入 `seed/refunds.json`。如果主线程愿意，可在 `Db` 里加 `refunds: Refund[]`，非必须。
6. 种子：`deploy/tools/gen-farm-seed.py`（UI agent 持有）若按 seed 目录逐文件生成 SQL，需要把 `refunds` -> `farm_refund` 映射加进去。我没改，下面附了可直接并入 `farm-seed.sql` 的手写 INSERT。
7. `ErrorCodeConstants.java`：我把三个新错误码放在独立文件 `FarmRefundErrorCodes.java`，主线程可并入后删掉这个文件，并把 `FarmRefundService` 里的静态 import 路径改回 `ErrorCodeConstants`。

## 3. 建表 SQL（PostgreSQL，并入 `backend/sql/postgresql/farm.sql`）

`farm.sql` 开头的 `DO $$ ... FOREACH t IN ARRAY [...]` 删表数组里加 `'farm_refund'`（该循环同时会建 `farm_refund_seq`，起始 1000）。表本身：

```sql
-- 退款单（第二期 A）。金额单位：分
CREATE TABLE farm_refund (
  id int8 PRIMARY KEY, no varchar(32) NOT NULL, order_id int8 NOT NULL, member_id int8 NOT NULL,
  amount int8 NOT NULL DEFAULT 0, invest_deduct int8 NOT NULL DEFAULT 0, deliver_deduct int8 NOT NULL DEFAULT 0,
  deduct int8 NOT NULL DEFAULT 0, refund_amount int8 NOT NULL DEFAULT 0,
  reason varchar(255) NOT NULL, status int2 NOT NULL DEFAULT 0, audit_remark varchar(255), prev_order_status int2 NOT NULL DEFAULT 10,
  audit_time timestamp,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE INDEX idx_farm_refund_order ON farm_refund (tenant_id, order_id);
CREATE INDEX idx_farm_refund_member ON farm_refund (tenant_id, member_id, create_time DESC);
CREATE INDEX idx_farm_refund_status ON farm_refund (tenant_id, status, create_time DESC);
```

`@KeySequence("farm_refund_seq")`（DO 已加）。`farm_order.status`、`farm_adoption.status`、`farm_task.status` 都是 `int2`，没有 CHECK 约束，新增取值 40/50/30/30 不需要改表。

`farm-seed.sql` 手写种子（和 `mock-server/seed/refunds.json` 同一条；末尾要加 `SELECT setval('farm_refund_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM farm_refund) + 1, 1000), false);`）：

```sql
INSERT INTO farm_refund (id, tenant_id, no, order_id, member_id, amount, invest_deduct, deliver_deduct, deduct, refund_amount, reason, status, audit_remark, prev_order_status, create_time, audit_time, creator) VALUES (1, 2, 'R202610300001', 2, 2, 86000, 1200, 0, 1200, 84800, '产出未达约定', 20, '已核对台账，订单仍在约定交付期内，暂不退款；如有疑问请联系管家。', 10, '2026-10-30 10:20:00', '2026-10-30 16:45:00', 'seed');
```

## 4. 契约接口表（并入 `farm-接口契约.md`）

### 4.1 状态与联动规则（mock 与 Java 一致，写在 `farm-refund.ts` 顶部注释）

- 退款单 `refund.status`：`0` 待审核、`10` 已通过、`20` 已驳回。
- 订单 `order.status` 新增：`40` 退款中、`50` 已退款。认养 `adoption.status` 新增：`30` 已终止。任务 `task.status` 新增：`30` 已关闭。
- **申请**：只有订单 `10 认养中` 可以申请；原因必填（trim 后非空，最长 200）。订单 `10 -> 40`，生成退款单（`status=0`，申请当时的试算金额存进退款单，经营者不能改）。
- **驳回**（审核意见必填）：退款单 `0 -> 20`；订单 `40 ->` 申请前的状态（即 10）；认养、地块、任务不动；认养人可以再次申请。
- **通过**：退款单 `0 -> 10`；订单 `40 -> 50`；认养 `10 -> 30`；认养的地块若是「已认养(10)」`-> 0 空闲`；认养名下待办(`0`)任务 `-> 30 已关闭`；认养名下待接单(`0`)代管工单 `-> 30 已取消`（已接单的不动）；合同台账按订单 50 显示「已解除」（见第 2 节第 1 条）。已发货的配送单不动。
- 款项原路退回是真实支付能力，演示环境不做，只改状态。
- 试算（对齐原型「系统试算（按合同第六条公式）」）：已过周期比例 = clamp((现在 - 开始) / (结束 - 开始), 0, 1)，认养开始前为 0；已发生农事投入 = round(实付 × 比例 × 20%)（20% 是演示系数，不是合同条款）；已交付农产品折算 = 有约定交付量时 round(实付 × min(1, 已收斤数 / 约定斤数))，没有约定量（半托管、不托管）为 0；扣减 = min(实付, 投入 + 折算)；应退 = 实付 - 扣减。

### 4.2 后台接口 `/admin-api/farm/refund/**`（经营者登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/refund/page` | 参数 `no`（退款单号，模糊）、`orderNo`（模糊）、`memberMobile`（模糊）、`status`、`pageNo, pageSize`。新的在前。字段见下面「退款单字段」 |
| GET | `/refund/stats` | `counts{all,0,10,20}`（页签数量）、`monthApply`（本月申请数）、`refundRate`（本月申请数 / 本月已付订单数，%，1 位小数，分母为 0 时 null）、`pending`、`pendingOver24h`（待审核且超 24h）、`avgHandleHours`（已审核单的申请到审核平均小时，1 位小数，没有则 null）、`refundedAmount`（已通过的应退合计，分）、`refundedCount`、`reasonAlert{reason,count}\|null`（近 30 天同一原因 >= 2 单） |
| GET | `/refund/get?id=` | 退款单字段 + `adoption{id,startAt,endAt,promisedJin,harvestedJin}\|null` + `elapsedRate`（申请时已过周期比例 0-1） |
| PUT | `/refund/audit` | 请求体 `{id, approve:boolean, remark?}`。驳回时 `remark` 必填；通过时可空；最长 200。返回审核后的退款单。错误：`404` 退款单不存在；`400` 缺 approve / 驳回缺说明 / 说明超长；`1030000010` 退款单已被审核或订单状态已变 |

退款单字段（后台、C 端共用）：`id, no`（`R` + 日期 + 序号）`, orderId, orderNo, orderStatus, memberId, memberName, memberMobile, productTitle, productCover, assetCode, careMode, careModeName, amount`（实付）`, investDeduct, deliverDeduct, deduct`（= 两者之和）`, refundAmount`（应退）`, reason, status, statusName`（待审核 / 已通过 / 已驳回）`, auditRemark, prevOrderStatus, createTime, auditTime`。金额一律分。

### 4.3 C 端接口 `/app-api/farm/refund/**`（会员登录；给 C 端 agent 用）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/refund/create` | 请求体 `{orderId, reason}`。会员对**自己的**认养订单申请退款。成功返回退款单（字段同上，`status=0`，订单随即变 `40`）。错误：`400` 请填写退款原因（空 / 全空白）或原因超过 200 字；`404` 订单不存在（含别人的订单）；`1030000008` 该订单已有退款申请在处理中（订单已是 40）/ 该订单已退款（订单已是 50）；`1030000009` 订单当前状态不能申请退款（待付款 0、已完成 20、已取消 30）。被驳回后订单回到 10，可以再次申请 |
| GET | `/refund/list` | 我的退款单，新的在前，数组，字段同上 |
| GET | `/refund/preview?orderId=` | **额外加的**，给退款页的「退款金额试算」用：`{orderId, orderNo, canApply(订单是否为 10), orderStatus, amount, elapsedRate, investDeduct, deliverDeduct, deduct, refundAmount}`。别人的订单 404 |

C 端要注意：`/farm/order/list`、`/farm/order/get` 返回的 `status` 会出现 `40`（退款中）、`50`（已退款）；订单为 50 时 `adoptionStatus` 为 `30`（已终止），我的认养列表里这条认养的 `status` 也是 `30`，页面要有对应文案，不要当成认养中。退款页文案里的「合同第六条」「3 个工作日」是原型文案，演示环境没有真实打款。

### 4.4 客户管理 `/admin-api/farm/customer/**`、客户分层 `/admin-api/farm/customer-tier/**`、管家管理 `/admin-api/farm/steward-manage/**`（经营者登录，全部只读）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/customer/page` | 参数 `keyword`（姓名/手机号）、`city`、`tag`、`tier`（core/growth/new/sleep/other）、`productId`（买过该商品）、`activeDays`、`minAmount`/`maxAmount`（累计消费区间，分）、`expiring=1`（30 天内有认养到期）、`pageNo,pageSize`。按最近活跃倒序。字段 `id, name, mobile`（脱敏）`, city, level`（V1-V4）`, adoptCount, totalAmount, tags[], lastActive, tier, tierName, renewIntent, activeAdoptionCount, expiringCount` |
| GET | `/customer/stats` | `total, newThisMonth, adopting, expiring30, repeatRate`（已付订单 >= 2 的认养人 / 有已付订单的认养人，%，无则 null）`, avgOrderAmount`（累计消费 / 已付订单数，分）`, cities[], tags[]` |
| GET | `/customer-tier/summary` | `total`、`tiers[{key,name,level,levelType,rule,action,count}]`、`expiring{customerCount,adoptionCount,estimateAmount,remindDays[30,15,3]}`（预计续费金额 = 到期认养对应订单金额之和）、`packs[{name,count,buildWay,tier,query}]`（规则圈选的人群包，只读）、`updatedAt` |
| GET | `/steward-manage/page` | 参数 `keyword`、`baseId`。字段 `id`（用户编号）`, name, mobile`（脱敏）`, roleName, baseId, baseName, assetTotal, assetAdopted, memberCount`（服务的不同认养人，认养中）`, monthTaskTotal, monthTaskDone, monthTaskRate`（%，本月无任务 null）`, avgResponseMinutes`（代管工单接单耗时均值，无则 null）`, status`（恒 0 在岗） |
| GET | `/steward-manage/overview` | `ranking[{id,name,score}]`（按本月任务完成率）、`today{pendingTasks, pendingRecords, pendingServiceOrders}` |

口径（原型里没有数据来源的不编造）：
- 已付订单 = `payTime` 非空且订单状态 ∈ {10, 20, 40}；已退款(50)、已取消(30)不算。累计认养 = 已付订单数；累计消费 = 其金额合计；最近活跃 = 全部订单下单/支付时间的最大值。
- 等级 V1~V4：累计消费 < 1000 元 V1、< 3000 元 V2、< 8000 元 V3、其余 V4（区间取自原型样例，演示口径，不是会员积分体系）。
- 城市：收货地址里「省」之后的「xx市」；取不到为空。标签由规则派生：新客 / 多单客户 / 全托管用户 / 有退款记录（原型的「亲子家庭」等手工标签没有数据）。
- 分层（RFM，原型 custGroup 的四条规则加一个「其他」桶，按顺序判断）：无已付订单 -> 其他；最近活跃距今 > 180 天 -> 沉睡；已付订单 >= 3 或近 365 天消费 >= 8000 元 -> 核心；已付订单 == 2 或近 365 天消费 >= 3000 元 -> 成长；首单距今 <= 90 天 -> 新客；其余 -> 其他。续费倾向：核心高 / 成长中 / 新客「新客」/ 沉睡低 / 其他「—」。
- 到访率、满意度、续费券、人群包的新建与推送：没有数据来源或属于营销能力（已排除），页面显示「暂无数据」或「演示环境暂不支持」。
- 管家月度任务 = 本自然月内截止、状态为待办或已完成的任务；绩效只有任务完成率一个口径。

## 5. 错误码

| 码 | 常量 | 文案 |
|---|---|---|
| 1_030_000_008 | `REFUND_DUPLICATE` | 该订单已有退款申请在处理中 / 该订单已退款 |
| 1_030_000_009 | `REFUND_NOT_ALLOWED` | 该订单当前状态不能申请退款 |
| 1_030_000_010 | `REFUND_STATE_CHANGED` | 退款单状态已变化，请刷新 |

mock 对应：`REFUND_BIZ.DUPLICATE / NOT_ALLOWED / STATE`（在 `farm-refund.ts` 内，没有动 `BIZ`）。

## 6. 测试用例（可粘贴到 `test/farm.test.ts`，沿用文件里的 `admin / member / get / post / put` 辅助函数）

```ts
describe('第二期 A 退款闭环', () => {
  const REASON = '认养计划变更'

  it('申请：原因必填，只能申请自己的认养中订单，不能重复申请', async () => {
    const m = await member(2, '13800000001') // 周女士：订单 1、3 认养中
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: '  ' })).code).toBe(400)
    expect((await post('/app-api/farm/refund/create', m, { orderId: 2, reason: REASON })).code).toBe(404) // 陈先生的订单
    const m3 = await member(2, '13800000003') // 王女士：订单 5 待付款
    expect((await post('/app-api/farm/refund/create', m3, { orderId: 5, reason: REASON })).code).toBe(1_030_000_009)
    const ok1 = await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })
    expect(ok1.code).toBe(0)
    expect(ok1.data.status).toBe(0)
    expect(ok1.data.deduct).toBe(ok1.data.investDeduct + ok1.data.deliverDeduct)
    expect(ok1.data.refundAmount).toBe(ok1.data.amount - ok1.data.deduct)
    expect(ok1.data.orderStatus).toBe(40)
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).code).toBe(1_030_000_008)
    expect((await get('/app-api/farm/refund/list', m)).data).toHaveLength(1)
    expect((await get('/app-api/farm/order/get', m, { id: 1 })).data.status).toBe(40)
  })

  it('驳回：必须写说明；订单回到认养中，可再次申请', async () => {
    const m = await member(2, '13800000001')
    const a = await admin(2, 'qingshan')
    const id = (await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).data.id
    expect((await put('/admin-api/farm/refund/audit', a, { id, approve: false })).code).toBe(400)
    const r = await put('/admin-api/farm/refund/audit', a, { id, approve: false, remark: '仍在约定期内' })
    expect(r.code).toBe(0)
    expect(r.data.status).toBe(20)
    expect((await get('/app-api/farm/order/get', m, { id: 1 })).data.status).toBe(10)
    expect((await put('/admin-api/farm/refund/audit', a, { id, approve: false, remark: 'x' })).code).toBe(1_030_000_010) // 已审核
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).code).toBe(0)
  })

  it('通过：订单已退款、认养终止、地块释放为空闲、待办任务关闭、合同随订单（待主线程改 contractStatusOf 后）解除', async () => {
    const m = await member(2, '13800000001')
    const a = await admin(2, 'qingshan')
    const id = (await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).data.id
    const r = await put('/admin-api/farm/refund/audit', a, { id, approve: true })
    expect(r.code).toBe(0)
    expect(r.data.status).toBe(10)
    const order = (await get('/app-api/farm/order/get', m, { id: 1 })).data
    expect(order.status).toBe(50)
    expect(order.adoptionStatus).toBe(30)
    const assets = (await get('/admin-api/farm/asset/page', a, { pageSize: 100 })).data.list
    expect(assets.find((x: any) => x.id === 3).status).toBe(0) // 订单 1 绑定的地块
    const tasks = await get('/admin-api/farm/refund/stats', a)
    expect(tasks.data.refundedCount).toBe(1)
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).code).toBe(1_030_000_008) // 已退款
    // 主线程改 views.ts contractStatusOf 后再打开下面一行
    // expect((await get('/admin-api/farm/contract/page', a, { status: 20 })).data.list.map((c: any) => c.orderId)).toContain(1)
  })

  it('后台列表和统计：种子里有 1 条已驳回；未登录 401；平台租户看不到别家的', async () => {
    const a = await admin(2, 'qingshan')
    const page = await get('/admin-api/farm/refund/page', a)
    expect(page.data.total).toBe(1)
    expect(page.data.list[0].statusName).toBe('已驳回')
    expect((await get('/admin-api/farm/refund/stats', a)).data.counts.all).toBe(1)
    const other = await admin(3, 'lvye') // 绿野农庄管理员
    expect((await get('/admin-api/farm/refund/page', other)).data.total).toBe(0)
    expect((await request(app).get('/admin-api/farm/refund/page')).body.code).toBe(401)
  })
})

describe('第二期 A 客户管理 / 客户分层 / 管家管理', () => {
  it('客户名单由会员 + 订单派生', async () => {
    const a = await admin(2, 'qingshan')
    const page = await get('/admin-api/farm/customer/page', a)
    expect(page.data.total).toBe(3)
    const zhou = page.data.list.find((c: any) => c.name === '周女士')
    expect(zhou.adoptCount).toBe(2)
    expect(zhou.totalAmount).toBe(214000)
    expect(zhou.level).toBe('V2')
    expect(zhou.city).toBe('杭州')
    expect(zhou.mobile).toBe('138****0001')
    expect((await get('/admin-api/farm/customer/page', a, { keyword: '13800000002' })).data.list.map((c: any) => c.name)).toEqual(['陈先生'])
    expect((await get('/admin-api/farm/customer/stats', a)).data.total).toBe(3)
  })

  it('分层人数加起来等于客户总数；已退款订单不计入累计消费', async () => {
    const a = await admin(2, 'qingshan')
    const s = (await get('/admin-api/farm/customer-tier/summary', a)).data
    expect(s.tiers.reduce((n: number, t: any) => n + t.count, 0)).toBe(s.total)
    const m = await member(2, '13800000002')
    const id = (await post('/app-api/farm/refund/create', m, { orderId: 2, reason: '计划变更' })).data.id
    await put('/admin-api/farm/refund/audit', a, { id, approve: true })
    const chen = (await get('/admin-api/farm/customer/page', a, { keyword: '陈先生' })).data.list[0]
    expect(chen.adoptCount).toBe(0)
    expect(chen.totalAmount).toBe(0)
    expect(chen.tags).toContain('有退款记录')
  })

  it('管家管理：本租户的管家，和管家端接口前缀不冲突', async () => {
    const a = await admin(2, 'qingshan')
    const page = await get('/admin-api/farm/steward-manage/page', a)
    expect(page.data.list.map((s: any) => s.name).sort()).toEqual(['张管家', '李管家'].sort())
    expect(page.data.list[0].mobile).toMatch(/\*\*\*\*/)
    const ov = await get('/admin-api/farm/steward-manage/overview', a)
    expect(ov.data.today).toHaveProperty('pendingRecords')
    expect((await get('/admin-api/farm/steward-manage/page', { })).code).toBe(401)
  })
})
```

> 测试依赖种子：订单 1 绑定的地块 id 是 3（`orders.json` 的 `assetId`）；`get(..., {})` 不带 token 头，用来断言 401。这些用例我没跑过，以服务器上的实际结果为准。

## 7. 没做 / 没验证 / 取舍

- **没验证**：Java 全部未编译；Vue 页面没有被 vite 解析过（本机 web-admin 无 node_modules）；vitest 没跑；登录后的任何页面都没看过。mock-server 只做了 `tsc --noEmit`，无报错 [实测]。
- **没做**（原型按钮，按方案「真实支付/营销排除」全部提示「演示环境暂不支持该操作」）：导出退款台账、联系客户、导出名单、批量打标签、新增客户、客户档案/发消息/关怀、新建人群包、人群包推送、派单、添加管家、绩效/调整。
- **取舍**：① 原型客户分层页的「自动推送续费券 ¥180」和人群包「关联活动」属于营销券，已排除，没做；人群包改为只读的规则圈选。② 到访率、满意度没有数据来源，页面写「暂无数据」，没有编造。③ 管家「角色」原型是种植/养殖/技术员/客服，系统只有「管家」一种角色，列里显示「管家」。④ 管家在岗状态用户表没有字段，恒为在岗。⑤ 订单详情页的「处理退款」快捷按钮属订单详情（UI/订单 agent 的文件），我没改。⑥ 试算里 20% 的投入系数是演示值，真实合同公式需要业务给口径。
- **已知风险**：退款通过会释放地块，若该地块已经因其他原因被停用(20)则不动（条件更新只改 10 -> 0）；已发货的配送单、已完成的代管工单不回滚；已接单(10)的代管工单不会被取消，需要人工处理。
- **Java 与 mock 的已知差异**：单号序号 mock 3 位、Java 4 位（沿用 FarmNoGenerator 的既有差异）；管家名单 mock 取 `users.kind=steward`，Java 取 `farm_steward` 表（同一批人）；管家角色 mock 取角色名，Java 固定「管家」。
