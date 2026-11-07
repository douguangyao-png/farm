# 第二期登记：C 端（app-member 缺页）

状态：代码已写，未在本机跑测试/类型检查（mock-server 本机 `tsc --noEmit` 通过 [实测]；vue 文件用 @vue/compiler-sfc 解析通过 [实测]；Java 只目测，未编译）。

## ① app.ts 里要加的 import / 注册行
```ts
import { farmAppExtraRouter } from './routes/farm-app-extra.js'
// 与 farmAppRouter 同一前缀，路径不重叠，放在 app.use('/app-api/farm', farmAppRouter) 之前或之后均可
app.use('/app-api/farm', farmAppExtraRouter)
```
注意：`/search`、`/base/*`、`/review/list` 不需要登录，其余在路由内用 `requireLogin('member')`。

## ② 建表 SQL（PostgreSQL，追加到 farm.sql；Java 的 @KeySequence 为 farm_app_extra_seq）
一张表按 kind 区分，避免为 7 类小数据各建一张表（用户可否决，改成多表时 Service 只需换 Mapper）。
```sql
CREATE TABLE farm_app_extra (
  id int8 PRIMARY KEY, member_id int8 NOT NULL, kind varchar(16) NOT NULL, target_id int8,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  creator varchar(64) DEFAULT '', create_time timestamp NOT NULL DEFAULT now(),
  updater varchar(64) DEFAULT '', update_time timestamp NOT NULL DEFAULT now(),
  deleted bool NOT NULL DEFAULT false, tenant_id int8 NOT NULL DEFAULT 0
);
CREATE SEQUENCE farm_app_extra_seq;
CREATE INDEX idx_farm_app_extra_member ON farm_app_extra (tenant_id, member_id, kind);
CREATE INDEX idx_farm_app_extra_target ON farm_app_extra (tenant_id, kind, target_id);
COMMENT ON TABLE farm_app_extra IS 'C 端会员小数据：favorite 收藏 / address 地址 / review 评价(target=商品) / visit 预约到访(target=基地) / transfer 转赠(target=认养) / chat 管家聊天 / msg_read 消息已读';
```
（列名风格以现有表为准，合并时请对照 farm.sql 里其它表的 creator/updater 列类型。）

## ③ 契约接口表（/app-api/farm 下，响应均为 `{code,data,msg}`）
| 方法 路径 | 登录 | 入参 | data |
|---|---|---|---|
| GET /search/hot | 否 | — | string[] 热门词 |
| GET /search | 否 | keyword?, type?(plot/animal/tree), sort?(default/sold/price) | {products[], bases[]}；products 项 = id,title,cover,assetType,categoryName,baseName,minPrice,sold,stock（Mock 的是完整 productView，字段超集） |
| GET /base/list | 否 | — | 基地卡片[]：id,name,cover,address,areaMu,cropSummary,certNo,productCount,rating(null=暂无评价),reviewCount |
| GET /base/get | 否 | id | 基地卡片 + lng,lat,products[],certs[{name,no}],news[],reviews[] |
| GET /favorite/list | 是 | type?(product/base) | [{id,type,targetId,product?/base?}] |
| GET /favorite/check | 是 | type,targetId | boolean |
| POST /favorite/toggle | 是 | {type,targetId} | boolean（切换后是否已收藏） |
| GET /address/list | 是 | — | [{id,name,mobile,detail,isDefault}]；没有地址时用会员资料里的地址带出一条 id=0 |
| POST /address/save | 是 | {id?,name,mobile,detail,isDefault} | id；校验：收货人必填、手机号 11 位、地址必填 |
| POST /address/delete | 是 | {id} | true |
| GET /message/list | 是 | type?(system/logistics/farming/interact) | {unread, list:[{key,type,title,content,createTime,link,read}]}，由订单/配送/农事记录/管家回复/转赠派生 |
| POST /message/read | 是 | {key?}（不传=全部已读） | true |
| GET /record/get | 是 | id | 农事记录详情（含 env、checks、evidenceNo、workHours=null） |
| GET /report/get | 是 | adoptionId | 质检报告（演示数据，demo=true） |
| GET /logistics/get | 是 | id（配送单） | 配送单 + nth + items[] + steward |
| GET /transfer/list、POST /transfer/create | 是 | {adoptionId,toMobile,scope(all/right),message?} | {id,no}；只记录申请，不改归属（演示） |
| GET /review/list（否）、GET /review/mine、POST /review/create | 创建需登录 | {adoptionId,score(1-5),dims{freshness,service,logistics},content?} | id；同一认养只能评一次 |
| GET /visit/list、POST /visit/create | 是 | {baseId,date(YYYY-MM-DD),people(1-20),meal,items[],remark?} | {id,no}；日期不能早于今天 |
| GET /service/messages、POST /service/send | 是 | {content} | 聊天记录；发送后自动追加一条演示回复 |

退款（A 提供，C 端按约定调用）：`POST /app-api/farm/refund/create {orderId, reason}`、`GET /app-api/farm/refund/list`。
**待对 A 的契约**：①`reason` 目前把「原因 + 补充说明」拼成一个字符串传，A 若有独立 `remark` 字段需改 refund/apply.vue；②列表项 C 端读取 `id,no,orderId,orderNo,amount,reason,status,statusName`（缺哪个就不显示哪个），status 暂按 0 待审核/10 已同意/20 已退款/30 已驳回显示，驳回的退款单不挡住再次申请；③创建失败（订单状态不允许）依赖接口返回的错误提示。

## ④ 测试用例（可粘贴 vitest，沿用 farm.test.ts 的登录/请求辅助，辅助函数名以该文件为准）
```ts
describe('C 端第二期补页', () => {
  it('搜索不需要登录，按关键词和品类过滤', async () => {
    const r = await app.get('/app-api/farm/search').query({ keyword: '地' }).set('tenant-id', '1')
    expect(r.body.code).toBe(0)
    expect(Array.isArray(r.body.data.products)).toBe(true)
    const t = await app.get('/app-api/farm/search').query({ type: 'plot' }).set('tenant-id', '1')
    expect(t.body.data.products.every((p: any) => p.assetType === 'plot')).toBe(true)
  })
  it('收藏切换两次回到未收藏', async () => {
    const a = await memberPost('/app-api/farm/favorite/toggle', { type: 'product', targetId: 1 })
    expect(a.body.data).toBe(true)
    const b = await memberPost('/app-api/farm/favorite/toggle', { type: 'product', targetId: 1 })
    expect(b.body.data).toBe(false)
  })
  it('地址：手机号格式不对返回 400；保存后第一条自动默认', async () => {
    expect((await memberPost('/app-api/farm/address/save', { name: '张', mobile: '123', detail: 'x' })).body.code).toBe(400)
    const ok = await memberPost('/app-api/farm/address/save', { name: '张', mobile: '13800138000', detail: '某路 1 号' })
    expect(ok.body.code).toBe(0)
  })
  it('评价：同一认养只能评一次；别人的认养 404', async () => {
    const first = await memberPost('/app-api/farm/review/create', { adoptionId: 1, score: 5 })
    expect(first.body.code).toBe(0)
    expect((await memberPost('/app-api/farm/review/create', { adoptionId: 1, score: 4 })).body.code).not.toBe(0)
  })
  it('预约到访：过去的日期被拒绝', async () => {
    const r = await memberPost('/app-api/farm/visit/create', { baseId: 1, date: '2020-01-01', people: 2 })
    expect(r.body.code).toBe(400)
  })
  it('农事记录详情、物流详情只能看自己的', async () => {
    expect((await memberGet('/app-api/farm/record/get?id=99999')).body.code).toBe(404)
    expect((await memberGet('/app-api/farm/logistics/get?id=99999')).body.code).toBe(404)
  })
  // 走真实装配的兜底（Java 侧）：FarmAppExtraController 的 Bean、farm_app_extra 表和序列存在，
  // 在服务器上调一次 GET /app-api/farm/base/list 即可发现「缺 Mapper / 缺表」
})
```

## ⑤ 错误码
没有新增。复用：400 BAD_REQUEST（校验）、404 NOT_FOUND、ORDER_STATUS_CHANGED（1_030_000_002，用于「只有认养中可转赠 / 已有待确认转赠 / 已评价过」；Mock 对应 BIZ.ORDER_STATE）。

## 新增 / 修改文件
- 新增 mock：`mock-server/src/routes/farm-app-extra.ts`
- 新增 Java：`dal/dataobject/FarmAppExtraDO.java`、`dal/mysql/FarmAppExtraMapper.java`、`service/FarmAppExtraService.java`、`controller/app/extra/FarmAppExtraController.java`
- app-member 新增页面（`pages/farm/` 下）：search/index、mall/index、base/list、base/detail、favorite/list、address/list、message/list、settings/index、record/detail、report/index、logistics/detail、transfer/index、review/edit、visit/index、service/chat、refund/apply，组件 components/product-card.vue
- app-member 修改：`pages.json`（16 页）、`sheep/api/farm/index.js`（新接口）、`pages/farm/mine.vue`（入口）、`index.vue`（搜索条 + 4 个快捷入口）、`order/list.vue`（退款页签、申请退款、去评价）、`adoption/detail.vue`（质检/到访/管家/转赠/评价入口、记录详情、物流详情链接）、`shipment/list.vue`（物流详情链接）、`product/detail.vue`（收藏、评价列表）

## 没做 / 简化
- 地址页提供选择模式（`?select=1`，写 storage `farm_picked_address`），但 order/confirm.vue 未接（不动第一期页面的收货人表单）。
- 未做：金刚区 10 项、秋播横幅、直播、会员/积分/券/邀请/问诊/社区（方案已排除）；合同独立页与手写签名；确认订单的交付方式三选一；农资独立结算页。
- 转赠只记录申请；在线管家为自动回复；质检报告、存证编号为演示数据；工时系统未采集，显示「系统暂未采集」。
- 基地评分只由真实评价算，无评价显示「暂无评分」。
