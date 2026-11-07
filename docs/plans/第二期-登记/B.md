# 第二期登记：B（交易流水、分账结算、经营分析、资质证照、资金公示、IoT 设备、视频监控、托管模式配置、消息模板）

状态：代码已写完。**本机实测过的**：mock-server `tsc --noEmit` 无报错；第 4 节的 7 个 vitest 用例在本机跑过一次，7 个全过（用临时测试文件把我的路由挂在 createApp 之外跑的，已删除，没碰 `test/farm.test.ts`）；9 个 Vue 文件（加 `MetricRow.vue`）用 `@vue/compiler-sfc` 做过解析和 template/script 编译，无错误。**没验证的**：Java 全部只做了目测，没编译、没跑；web-admin 没起 vite、没在浏览器里看过任何一页；登录后的页面我没验证，请主线程部署后走一遍。

## 0. 我新增的文件（没有改任何共享文件）

| 层 | 文件 |
|---|---|
| mock | `mock-server/src/farm/ext-b.ts`（派生逻辑 + 少量内存状态，状态挂在当前 Db 对象上，`/__mock/reset` 后自动清空）、`mock-server/src/routes/farm-ext-b.ts`（路由） |
| web-admin 页面 | `views/farm/{finance-flow,finance-split,analysis,compliance-cert,compliance-fund,asset-device,camera,hosting-config,msg-template}/index.vue`；`analysis/MetricRow.vue`；`finance-flow|compliance-cert|compliance-fund|asset-device/constants.ts` |
| web-admin API | `api/farm/{finance,compliance,device,hosting,msgTemplate}.ts` |
| Java | `dal/dataobject/{FarmCertDO,FarmFundNoticeDO,FarmMsgTemplateDO,FarmDeviceDO}.java`、`dal/mysql/` 对应 4 个 Mapper、`service/{FarmFinanceService,FarmAnalysisService,FarmDeviceService,FarmCertService,FarmFundNoticeService,FarmMsgTemplateService,FarmHostingConfigService}.java`、`controller/admin/{finance,cert,device,hostingconfig,msgtemplate}/**`（含 `fundnotice/vo`；资金公示的两个接口放在 `FarmCertController` 里，经营分析接口放在 `FarmFinanceController` 里） |
| 文档 | 本文件；`docs/plans/第二期-权限清单.md` 的「B」小节 |

（Java 包路径前缀 `backend/yudao-module-farm/src/main/java/cn/iocoder/yudao/module/farm/`。）

## 1. app.ts 里要加的 import / 注册行

```ts
import { farmExtBRouter } from './routes/farm-ext-b.js'
```

注册（放在 `app.use('/admin-api/farm', farmAdminRouter)` 之前或之后都行，我的路径和它不重叠；但**必须在最后的兜底 `app.use((req,res)=>…)` 之前**）：

```ts
  app.use('/admin-api/farm', farmExtBRouter)
```

用到的路径前缀：`/finance/flow`、`/finance/split`、`/analysis`、`/cert`、`/fund-notice`、`/device`、`/camera`、`/hosting-config`、`/msg-template`。

## 2. 建表 SQL（PostgreSQL）和种子

加进 `backend/sql/postgresql/farm.sql`，并把四个表名加进文件开头 DROP/序列循环的数组里（`'farm_cert','farm_fund_notice','farm_msg_template','farm_device'`；序列名 = 表名 + `_seq`，起始 1000，对应 DO 上的 `@KeySequence`）。字段口径同现有表：带 tenant_id、creator/create_time/updater/update_time/deleted。

```sql
-- 资质证照：预置演示证照（demo=1）+ 经营者上传的证照。基地备案证不入这张表，由 farm_base.cert_no 派生
CREATE TABLE farm_cert (
  id int8 PRIMARY KEY, name varchar(100) NOT NULL, no varchar(100) NOT NULL, cert_type varchar(8) NOT NULL,
  valid_until timestamp, verify varchar(16) NOT NULL DEFAULT 'pending', public_mode varchar(16) NOT NULL DEFAULT 'public',
  file_url text DEFAULT '', demo int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 资金公示开关：每个租户一行，fields 是 JSON（text 存，JacksonTypeHandler 读写）
CREATE TABLE farm_fund_notice (
  id int8 PRIMARY KEY, fields text NOT NULL DEFAULT '{}',
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- 消息模板（trigger 是 SQL 关键字，列名用 trigger_text）
CREATE TABLE farm_msg_template (
  id int8 PRIMARY KEY, name varchar(100) NOT NULL, trigger_text varchar(100) NOT NULL, channels text NOT NULL DEFAULT '[]',
  content text NOT NULL, status int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
-- IoT 设备台账：custom=1 是手动添加的设备；custom=0 是对「资产派生设备」的覆盖（改位置/固件、派单），code 与派生编号一致
CREATE TABLE farm_device (
  id int8 PRIMARY KEY, code varchar(32) NOT NULL, custom int2 NOT NULL DEFAULT 0, kind varchar(16) NOT NULL DEFAULT '',
  type_name varchar(32) NOT NULL DEFAULT '', name varchar(100) NOT NULL DEFAULT '', position varchar(100) DEFAULT '',
  bind_text varchar(255) DEFAULT '', firmware varchar(32) DEFAULT '', dispatched int2 NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted int2 NOT NULL DEFAULT 0, tenant_id int8 NOT NULL DEFAULT 0
);
```

种子（加进 `farm-seed.sql`；如果该文件由 `deploy/tools/gen-farm-seed.py` 生成，请并进脚本。mock 里这些数据按租户首次访问时现生成，SQL 里写死租户 2、3）：

```sql
-- 预置演示证照（每个经营者租户 6 份；检测报告「有效期」取今天 +25 天，演示「30 天内到期」）
INSERT INTO farm_cert (id, tenant_id, name, no, cert_type, valid_until, verify, public_mode, demo, creator) VALUES
 (101, 2, '营业执照', '9233****A', '必备', NULL, 'verified', 'public', 1, 'seed'),
 (102, 2, '土地流转合同', 'LH-2021-0088', '必备', '2029-03-01', 'verified', 'private', 1, 'seed'),
 (103, 2, '农产品质量检测报告', 'CNAS-2026-0918', '建议', CURRENT_DATE + 25, 'verified', 'public', 1, 'seed'),
 (104, 2, '政策性农业保险保单', 'PICC-2026-0188', '必备', '2027-03-15', 'verified', 'public', 1, 'seed'),
 (105, 2, '动物防疫条件合格证', 'FZ-2024-0116', '必备', '2027-06-30', 'verified', 'public', 1, 'seed'),
 (106, 2, '食品经营许可证', 'JY-2023-0421', '按需', '2028-05-20', 'renew', 'public', 1, 'seed'),
 (111, 3, '营业执照', '9233****A', '必备', NULL, 'verified', 'public', 1, 'seed'),
 (112, 3, '土地流转合同', 'LH-2021-0088', '必备', '2029-03-01', 'verified', 'private', 1, 'seed'),
 (113, 3, '农产品质量检测报告', 'CNAS-2026-0918', '建议', CURRENT_DATE + 25, 'verified', 'public', 1, 'seed'),
 (114, 3, '政策性农业保险保单', 'PICC-2026-0188', '必备', '2027-03-15', 'verified', 'public', 1, 'seed'),
 (115, 3, '动物防疫条件合格证', 'FZ-2024-0116', '必备', '2027-06-30', 'verified', 'public', 1, 'seed'),
 (116, 3, '食品经营许可证', 'JY-2023-0421', '按需', '2028-05-20', 'renew', 'public', 1, 'seed');

-- 消息模板（每个经营者租户 6 条）
INSERT INTO farm_msg_template (id, tenant_id, name, trigger_text, channels, content, status, creator)
SELECT t.base + n.i, t.tid, n.name, n.trg, n.ch, n.content, 0, 'seed'
FROM (VALUES (2, 100), (3, 110)) AS t(tid, base),
     (VALUES
       (1, '支付成功通知', '订单支付完成', '["订阅消息"]', '您的认养已生效，合同编号 HT-XXXXXXXX，专属管家将尽快与您联系。'),
       (2, '农事动态提醒', '管家发布记录', '["订阅消息"]', '您家 {地块编号} 有新动态：{记录标题}，点击查看现场照片。'),
       (3, '配送发货通知', '批次发货', '["订阅消息","短信"]', '您的第 {n} 次配送已发出，{承运商}，物流单号 {单号}。'),
       (4, '到期续费提醒', '到期前 30/15/3 天', '["订阅消息"]', '您的认养将于 {到期日} 到期，续养可享老客专属价格。'),
       (5, '异常预警', '设备离线 / 画面静止', '["App 推送"]', '{设备编号} 已离线 30 分钟，请及时处理。'),
       (6, '退款进度', '退款状态变更', '["订阅消息"]', '您的退款申请已通过，款项将在 3 个工作日内原路退回。')
     ) AS n(i, name, trg, ch, content);
```

`farm_fund_notice`、`farm_device` 不需要种子（没有行时按默认值：前 5 项公示、后 2 项不公示；设备全部从资产派生）。

## 3. 契约接口表（加进 `docs/api/farm-接口契约.md`，前缀 `/admin-api/farm`；金额单位：分；时间：毫秒时间戳）

分页接口统一返回 `{ list, total, stats }`（Java 用已有的 `FarmFulfillPage`）。无新错误码，参数错误 400、找不到 404。

| 方法 路径 | 入参 | 返回要点 |
|---|---|---|
| GET `/finance/flow/page` | pageNo, pageSize, tradeNo(模糊), memberName(模糊), type(adopt/supply/service), settleStatus(settled/settling), beginTime, endTime | 行：tradeNo、memberName、type、typeName、amount、fee(3%)、net、settleStatus、settleStatusName、time、refNo；stats：todayAmount/todayCount、last30Amount/last30Count/last30Growth(%)、last30Fee、feeRate、pendingAmount/pendingCount。**流水由认养订单(已付未取消)+农资订单+已完成代管工单派生；服务费 3%、T+1 为演示口径**；stats 看全部流水，不受筛选影响 |
| GET `/finance/split/get` | - | demo、sampleProductTitle、exampleAmount、rules[{key,name,type,percent,exampleAmount}]（88/3/6/3，演示）、account（演示）、month{label,total,items[]}（近 30 天，合计=流水合计）、serviceSplit{count,total,steward,farm,platform}（**真实**，取代管工单 split 比例）、invoices[]（演示）、feeRate |
| GET `/analysis/summary` | days=7/30/90（默认 30） | funnel[{name,value,rate,demo}]（6 环节，曝光/浏览/首季续费为演示，下单/支付/签约为订单实算）、channels[]（演示，合计=已支付单数）、categories[]（真实 GMV：认养按商品类型 + 农资商品 + 代管服务）、retention[]/quality[]（每项 {label,value,unit,demo,hint}）、paidCount |
| GET `/cert/page` | pageNo, pageSize, name, type(必备/建议/按需) | 行含 verify(verified/pending/renew/expired，过期由有效期派生)、verifyName、daysLeft、expiringSoon、publicMode/publicName、editable、source(upload/base)、demo；stats：total/expiringSoon/expired/missingRequired + records[]（核验记录时间线） |
| POST `/cert/create` | name, no, type, validUntil(ms 或 null=长期), publicMode(public/private), fileUrl | 返回新 id；状态固定「待核验」。名称/编号为空、类型不在三值内回 400 |
| PUT `/cert/update-public` | id, publicMode | 基地备案证(id≥9000)回 400「来自基地资料」 |
| GET `/fund-notice/get` | - | fields[{key,label,recommend,enabled}]、preview（payeeName=租户名、channelName、feeRate、policyNo=证照里含「保单」的那份、monthlyTotal、memberTotal、steps）、usage[{name,percent,amount,demo}]（近 30 天；**占比为演示，仅平台服务费一行真实**）、usageTotal、updateTime |
| PUT `/fund-notice/save` | fields:{flowChart,payeeName,channelName,feeRate,policyNo,monthlyTotal,memberTotal: boolean} | 只更新传了 boolean 的键 |
| GET `/device/page` | pageNo, pageSize, code, kind(camera/sensor), status(online/offline) | 行：code(CAM-01/SEN-01/手动添加的 CAM-X01)、kind、typeName、position、bindText、assetCodes、status、statusName、lastReportTime、firmware、dispatched、custom；stats：total/cameraCount/sensorCount/onlineCount/offlineCount/todayReports/retainDays/storage*。**设备清单取自资产上的 cameraName/sensorName 去重；状态(摄像头 ≥3 台时最后一台离线)、上报时间、固件为演示值** |
| POST `/device/create` | kind, typeName, name, position, bindText | 返回设备编号；名称为空 400 |
| PUT `/device/update` | code, position, firmware | 找不到设备 404 |
| PUT `/device/dispatch` | code | 非离线设备回 400「只有离线设备可以派单」 |
| GET `/camera/overview` | - | demo、stats{cameraCount,onlineCount,offlineCount,todayViews,watchHours,retainDays,alertCount}、cameras[]（同设备行 + visibleMembers[真实：绑定标的上在养的认养人数]、todayViews[演示]）、policy[{k,v}] |
| GET `/hosting-config/get` | productId（缺省取本租户第一个商品） | product、products[]、modes[3]{mode,name,configured,enabled,price,priceDelta(相对全托管),slogan,outputPromise,contractVersion,contractName,weeklyTodo,insurance,duty,dutyFarmCount,dutyMemberCount,supplyPackage[]}、dutyMatrix[{item,full,semi,self}]、switchRule、trigger{…,overdueIncome,overdueCount(近 30 天由任务转的代管工单，真实)}、compliance{pesticideBlocked,rules[]}。**只读；保存直接用已有 `PUT /farm/product/update`（传 id + careModes），口径与商品表单是同一份** |
| GET `/msg-template/page` | pageNo, pageSize, name, status(0 启用/1 停用) | 行：id,name,trigger,channels[],content,status |
| POST `/msg-template/create`、PUT `/msg-template/update` | name, trigger, channels(订阅消息/短信/App 推送), content（update 另传 id，未传的字段沿用） | **名称或内容命中金融类违禁词：返回 code 1030000000（沿用 `PRODUCT_BANNED_WORDS`），data:{words,suggestion}**，同认养商品 |
| PUT `/msg-template/update-status` | id, status | - |
| POST `/msg-template/test-send` | id | 不真实下发：{sent:false, demo:true, channels, preview}；已停用的模板回 400 |

## 4. 测试用例（粘进 `mock-server/test/farm.test.ts`，用文件里已有的 `admin/get/post/put/app`；依赖第 1 节的 app.ts 注册）

```ts
describe('第二期 B：交易流水、分账、经营分析', () => {
  it('未登录返回 401；流水由订单派生，金额恒等、交易号唯一、租户隔离', async () => {
    const r401 = await request(app).get('/admin-api/farm/finance/flow/page')
    expect(r401.body.code).toBe(401)
    const h = await admin(2, 'qingshan')
    const r = await get('/admin-api/farm/finance/flow/page', h, { pageSize: 100 })
    expect(r.code).toBe(0)
    expect(r.data.total).toBeGreaterThan(0)
    const rows = r.data.list as { tradeNo: string; amount: number; fee: number; net: number; type: string }[]
    for (const x of rows) expect(x.net).toBe(x.amount - x.fee)
    expect(new Set(rows.map((x) => x.tradeNo)).size).toBe(rows.length)
    expect(r.data.stats.feeRate).toBe(3)
    // 只按类型筛，其余不变
    const adopt = await get('/admin-api/farm/finance/flow/page', h, { type: 'adopt', pageSize: 100 })
    expect(adopt.data.list.every((x: { type: string }) => x.type === 'adopt')).toBe(true)
    // 另一个租户看不到本租户的流水
    const h3 = await admin(3, 'lvye')
    const r3 = await get('/admin-api/farm/finance/flow/page', h3, { pageSize: 100 })
    const nos2 = new Set(rows.map((x) => x.tradeNo))
    expect(r3.data.list.every((x: { refNo: string }) => !x.refNo.startsWith('RY'))).toBe(true)
    expect(r3.data.total).toBeLessThan(r.data.total)
    void nos2
  })

  it('分账规则合计 100%，示例金额拆分后合计不变；经营分析的渠道合计等于已支付单数', async () => {
    const h = await admin(2, 'qingshan')
    const s = await get('/admin-api/farm/finance/split/get', h)
    expect(s.data.rules.reduce((a: number, x: { percent: number }) => a + x.percent, 0)).toBe(100)
    expect(s.data.rules.reduce((a: number, x: { exampleAmount: number }) => a + x.exampleAmount, 0)).toBe(s.data.exampleAmount)
    expect(s.data.month.items.reduce((a: number, x: { amount: number }) => a + x.amount, 0)).toBe(s.data.month.total)
    const a = await get('/admin-api/farm/analysis/summary', h, { days: 90 })
    expect(a.data.days).toBe(90)
    expect(a.data.funnel.map((x: { name: string }) => x.name)).toEqual(['曝光次数', '商品浏览', '进入下单', '完成支付', '签署合同', '首季续费'])
    expect(a.data.channels.reduce((n: number, x: { value: number }) => n + x.value, 0)).toBe(a.data.paidCount)
    expect(a.data.retention.some((x: { demo: boolean }) => x.demo)).toBe(true)
  })
})

describe('第二期 B：合规中心（资质证照、资金公示）', () => {
  it('上传证照进入待核验；类型不对 400；基地备案证只读；重置后清空', async () => {
    const h = await admin(2, 'qingshan')
    const bad = await post('/admin-api/farm/cert/create', h, { name: '检测报告', no: 'X1', type: '随便' })
    expect(bad.code).toBe(400)
    const ok = await post('/admin-api/farm/cert/create', h, { name: '新版检测报告', no: 'CNAS-2026-1', type: '建议', validUntil: Date.now() + 10 * 86400_000, publicMode: 'private' })
    expect(ok.code).toBe(0)
    const list = await get('/admin-api/farm/cert/page', h, { pageSize: 100 })
    const mine = list.data.list.find((x: { id: number }) => x.id === ok.data)
    expect(mine.verify).toBe('pending')
    expect(mine.expiringSoon).toBe(true)
    expect(list.data.stats.records.some((x: { text: string }) => x.text.includes('新版检测报告'))).toBe(true)
    const t = await put('/admin-api/farm/cert/update-public', h, { id: ok.data, publicMode: 'public' })
    expect(t.code).toBe(0)
    const base = list.data.list.find((x: { source: string }) => x.source === 'base')
    expect(base.editable).toBe(false)
    const ro = await put('/admin-api/farm/cert/update-public', h, { id: base.id, publicMode: 'private' })
    expect(ro.code).toBe(400)
    await request(app).post('/__mock/reset')
    const h2 = await admin(2, 'qingshan')
    const after = await get('/admin-api/farm/cert/page', h2, { pageSize: 100 })
    expect(after.data.list.some((x: { name: string }) => x.name === '新版检测报告')).toBe(false)
  })

  it('资金公示：开关保存后能读到；预览里的保单编号取自证照', async () => {
    const h = await admin(2, 'qingshan')
    const before = await get('/admin-api/farm/fund-notice/get', h)
    expect(before.data.fields.find((f: { key: string }) => f.key === 'monthlyTotal').enabled).toBe(false)
    expect(before.data.preview.policyNo).toBe('PICC-2026-0188')
    const s = await put('/admin-api/farm/fund-notice/save', h, { fields: { monthlyTotal: true, flowChart: false } })
    expect(s.code).toBe(0)
    const after = await get('/admin-api/farm/fund-notice/get', h)
    expect(after.data.fields.find((f: { key: string }) => f.key === 'monthlyTotal').enabled).toBe(true)
    expect(after.data.fields.find((f: { key: string }) => f.key === 'flowChart').enabled).toBe(false)
    expect(after.data.usage.reduce((n: number, x: { amount: number }) => n + x.amount, 0)).toBe(after.data.usageTotal)
  })
})

describe('第二期 B：IoT 设备、视频监控', () => {
  it('设备由资产派生；只有离线设备能派单；手动添加的设备进台账', async () => {
    const h = await admin(2, 'qingshan')
    const d = await get('/admin-api/farm/device/page', h, { pageSize: 100 })
    expect(d.data.stats.cameraCount).toBe(5)
    const offline = d.data.list.find((x: { status: string }) => x.status === 'offline')
    const online = d.data.list.find((x: { status: string }) => x.status === 'online')
    expect((await put('/admin-api/farm/device/dispatch', h, { code: online.code })).code).toBe(400)
    expect((await put('/admin-api/farm/device/dispatch', h, { code: offline.code })).code).toBe(0)
    const c = await post('/admin-api/farm/device/create', h, { kind: 'sensor', typeName: '气象站', name: 'B 区气象站' })
    expect(c.code).toBe(0)
    const d2 = await get('/admin-api/farm/device/page', h, { pageSize: 100 })
    expect(d2.data.list.find((x: { code: string }) => x.code === offline.code).dispatched).toBe(true)
    expect(d2.data.list.some((x: { code: string; custom: boolean }) => x.code === c.data && x.custom)).toBe(true)
    const cam = await get('/admin-api/farm/camera/overview', h)
    expect(cam.data.stats.cameraCount).toBe(5)
    expect(cam.data.cameras.every((x: { kind: string }) => x.kind === 'camera')).toBe(true)
  })
})

describe('第二期 B：托管模式配置、消息模板', () => {
  it('托管模式配置与认养商品是同一份三档口径；改商品后配置页同步变', async () => {
    const h = await admin(2, 'qingshan')
    const cfg = await get('/admin-api/farm/hosting-config/get', h, { productId: 1 })
    expect(cfg.code).toBe(0)
    const prod = (await get('/admin-api/farm/product/get', h, { id: 1 })).data
    for (const m of cfg.data.modes) expect(m.price).toBe(prod.careModes.find((c: { mode: string }) => c.mode === m.mode).price)
    expect(cfg.data.modes.map((m: { price: number }) => m.price)).toEqual([128000, 86000, 50000])
    expect(cfg.data.modes[1].priceDelta).toBe(-42000)
    expect(cfg.data.modes[0].supplyPackage).toEqual([])
    // 农药类不可零售，不会进默认农资包
    expect(cfg.data.modes[1].supplyPackage.every((s: { cat: string }) => s.cat !== 'pesticide')).toBe(true)
    const modes = prod.careModes.map((c: { mode: string; price: number }) => (c.mode === 'semi' ? { ...c, price: 90000 } : c))
    expect((await put('/admin-api/farm/product/update', h, { id: 1, careModes: modes })).code).toBe(0)
    const cfg2 = await get('/admin-api/farm/hosting-config/get', h, { productId: 1 })
    expect(cfg2.data.modes[1].price).toBe(90000)
    expect(cfg2.data.modes[1].priceDelta).toBe(-38000)
  })

  it('消息模板：违禁词被拦；停用后不能测试发送；测试发送只返回预览', async () => {
    const h = await admin(2, 'qingshan')
    const bad = await post('/admin-api/farm/msg-template/create', h, { name: '活动', trigger: '下单', channels: ['短信'], content: '稳赚不赔，回报丰厚' })
    expect(bad.code).toBe(1_030_000_000)
    expect(bad.data.words).toEqual(expect.arrayContaining(['稳赚', '回报']))
    const okc = await post('/admin-api/farm/msg-template/create', h, { name: '到园提醒', trigger: '预约前一天', channels: ['订阅消息'], content: '您预约的 {日期} 到访已确认' })
    expect(okc.code).toBe(0)
    const sent = await post('/admin-api/farm/msg-template/test-send', h, { id: okc.data })
    expect(sent.data.sent).toBe(false)
    expect(sent.data.preview).toBe('您预约的 示例 到访已确认')
    await put('/admin-api/farm/msg-template/update-status', h, { id: okc.data, status: 1 })
    expect((await post('/admin-api/farm/msg-template/test-send', h, { id: okc.data })).code).toBe(400)
    const upd = await put('/admin-api/farm/msg-template/update', h, { id: okc.data, content: '年化收益' })
    expect(upd.code).toBe(1_030_000_000)
    const lst = await get('/admin-api/farm/msg-template/page', h, { pageSize: 50 })
    expect(lst.data.total).toBe(7)
  })
})
```

Java 侧「走真实装配」的兜底用例建议（我没写，本机不跑 Java）：起 Spring 上下文后调一遍 `GET /farm/finance/flow/page`、`/finance/split/get`、`/analysis/summary`、`/cert/page`、`/fund-notice/get`、`/device/page`、`/camera/overview`、`/hosting-config/get`、`/msg-template/page`，确认 4 个新 Mapper（`@Mapper` 在 `dal/mysql`，随现有扫描生效）和 `TenantService`、各 Service 的注入都能装配、SQL 能执行 [未验证]。

## 5. 错误码

没有新增。违禁词沿用 `PRODUCT_BANNED_WORDS`(1_030_000_000)，`FarmBannedWordsException` 原样复用；其余用 `BAD_REQUEST`/`NOT_FOUND`。

## 6. 口径说明与偏差（请主线程知悉）

1. **托管模式口径 [实测]**：价格、产出承诺、合同版本、责任矩阵全部来自商品的 `careModes`（一分菜地 1280/860/500，差价 -420/-780，与 docs/10 一致）；页面保存走已有的商品更新接口，违禁词校验也在那里。原型里写的「保底 240 斤，不足补足」**没有采用**，沿用现有文案「约定交付 240 斤，不足部分按同等品质补发」。「责任边界」在现有数据里不是独立字段，页面显示为按责任矩阵统计的「农场 N 项 / 认养人 M 项」，没有另造字段。
2. **IoT 告警规则的土壤湿度阈值**：原型 IoT 页写 35%，原型托管配置页写 22%，现有任务引擎/演示地块用 22%。页面统一写 22%（asset-device/constants.ts 有注释）。
3. **交易流水统计用滚动 30 天**，不用「本月」：演示库订单集中在 10 月，月初会全是 0。卡片标题写的是「近 30 天收款」。
4. 原型流水里有「退款」类型，**本期没做**：退款闭环是 A 的 `farm_refund`，没有接。等退款落库后，在 `flowRows` / `FarmFinanceService.flows` 里加一类 `refund`（负数）即可，不影响现有行。
5. 资质证照没有「删除」和「平台核验通过」：原型写明「历史版本不可删除」，核验由平台风控人工做，演示环境上传后一直是「待核验」。「必备证照缺失限制上架 / 过期自动下架」本期只展示规则文字，没有联动商品上架。
6. 视频监控的画面是占位（真实直播已排除），页面和接口都标了「演示」。「回放」「设置」「批量配置」「导出」「下载对账单」按钮只提示「演示版暂未开放」。
7. 页面里新增的按钮没有加 `v-hasPermi`（现有 farm 页面都没加，沿用「页面可见即可操作」），权限串见权限清单 B 小节；后端目前也没做 `@PreAuthorize`。
8. 视频监控页的「接入新设备」按钮跳转 `/farm-product/asset-device`（按 UI 小节的菜单路径写死）。
9. 资金公示页的预览要被 C 端「合规说明」页真正读取，需要 C 端 agent 另做一个只读接口（本期没做）。
