# 认养农场 SaaS · 业务接口契约（演示 Demo 版）

- 关联方案：`docs/plans/demo-四端演示Demo-2026-11-04.md`（T2）
- 状态：2026-11-05 写成，用户选择不单独确认、做完和页面一起评审
- 实现：`mock-server/src/routes/farm-*.ts`；以后真后端（芋道 ruoyi-vue-pro）照这份契约实现，前端只换接口地址

## 0. 通用约定

| 项 | 约定 | 依据 |
|---|---|---|
| 响应 | `{ code, data, msg }`，`code = 0` 成功 | 芋道 CommonResult |
| 分页 | 请求 `pageNo`（从 1 起）、`pageSize`（最大 200）；响应 `{ list, total }` | 芋道 PageResult |
| 金额 | 整数，单位**分**（¥1,280 = `128000`） | 芋道商城口径 |
| 重量 | 数字，单位**斤**，最多 1 位小数 | 文档 10 |
| 时间 | 毫秒时间戳（number） | 芋道返回 LocalDateTime 序列化为时间戳 |
| 租户 | 请求头 `tenant-id`；登录后 token 绑定租户，`tenant-id` 与 token 不一致返回 **403**；平台租户（id=1）可以访问全部租户 | R-6 |
| 鉴权 | `Authorization: Bearer <accessToken>`；未登录 **401** | 芋道 |
| 前缀 | 后台、平台、管家端：`/admin-api`；C 端：`/app-api` | 芋道 |
| 跨租户按 id 取数据 | 返回 **404**「数据不存在」（不暴露别的租户有这条数据） | — |
| 演示里不实现的写操作 | 返回 **501**「演示环境暂不支持该操作」 | mock 兜底 |

### 0.1 枚举

| 名称 | 取值 |
|---|---|
| 托管方式 `careMode` | `full` 全托管、`semi` 半托管、`self` 不托管 |
| 资产类型 `assetType` | `plot` 地块、`animal` 畜禽、`tree` 果树 |
| 资产状态 `asset.status` | `0` 空闲、`10` 已认养、`20` 停用 |
| 认养商品状态 `product.status` | `0` 上架、`1` 下架 |
| 认养订单状态 `order.status` | `0` 待付款、`10` 认养中、`20` 已完成、`30` 已取消、`40` 退款中、`50` 已退款（第二期，退款闭环） |
| 认养状态 `adoption.status` | `10` 认养中、`20` 已完成、`30` 已终止（第二期，退款通过后） |
| 退款单状态 `refund.status` | `0` 待审核、`10` 已通过、`20` 已驳回 |
| 农事记录状态 `record.status` | `0` 待审核、`10` 已通过、`20` 已驳回 |
| 农事任务状态 `task.status` | `0` 待办、`10` 已完成、`20` 已委托代办、`30` 已关闭（第二期，退款通过后） |
| 任务执行方 `task.owner` | `steward` 管家、`member` 认养人（自管任务） |
| 代管工单状态 `serviceOrder.status` | `0` 待接单、`10` 已接单、`20` 已完成、`30` 已取消（退款通过时，名下待接单工单置 30） |
| 农资类目 `supply.cat` | `seed` 种子、`fert` 肥料、`tool` 农具、`bio` 生物防治、`svc` 代管服务、`ins` 保险、`pesticide` 化学农药（只能农场代施，不零售） |
| 农资交付方式 `deliverWay` | `store` 寄存基地、`express` 快递到家、`pickup` 基地自提、`apply` 农场代施 |
| 投入品留痕 `traceState` | `0` 待登记、`10` 已写入 |
| 配送状态 `shipment.status` | `0` 待发货、`10` 运输中、`20` 已签收 |
| 风控预警类型 `riskAlert.type` | `promise` 承诺收益、`fake_base` 虚构基地、`brush` 刷单、`scope` 超范围经营 |
| 预警状态 `riskAlert.status` | `0` 待处理、`10` 已处理、`20` 已忽略 |
| 反馈类型 `feedback.type` | `bug` 问题、`advice` 建议、`ask` 咨询 |
| 反馈状态 `feedback.status` | `0` 待处理、`10` 处理中、`20` 已解决、`30` 已关闭 |

### 0.2 业务错误码

| code | msg（示例） | 触发 |
|---|---|---|
| 400 | 「标题不能为空」等 | 必填字段缺失、格式错误 |
| 1_030_000_000 | 「文案含有金融类违禁词：稳赚、收益。建议改为『约定交付 240 斤，不足部分按同等品质补发』这类实物交付说法」 | 认养商品的标题、描述、产出说明命中违禁词（R-2）。`data.words` 是命中的词数组，`data.suggestion` 是建议文案 |
| 1_030_000_001 | 「请先阅读并勾选认养合同」 | C 端下单时 `contractAgreed` 不是 `true`（R-3） |
| 1_030_000_002 | 「订单状态已变化，请刷新」 | 重复支付、对已取消订单支付 |
| 1_030_000_003 | 「这个商品暂时没有空闲的地块」 | 支付时找不到空闲资产 |
| 1_030_000_004 | 「请选择要使用这份农资的认养地块」 | 农资下单没带 `adoptionId`（文档 10：必须绑定地块） |
| 1_030_000_005 | 「该农资不能零售，只能由农场代施」 | 化学农药类农资零售下单 |
| 1_030_000_006 | 「任务状态已变化，请刷新」 | 已完成/已委托的任务再次完成或委托；工单重复接单 |
| 1_030_000_007 | 「只能处理分配给自己的任务」 | 管家操作别的管家的任务；第二期管家端新接口同码，文案为「只能处理自己负责的认养」 |
| 1_030_000_008 | 「该订单已有退款申请在处理中」/「该订单已退款」 | 订单已是 40（或有待审核退款单）、已是 50 时再次申请退款 |
| 1_030_000_009 | 「该订单当前状态不能申请退款」 | 订单不是 10 认养中（待付款、已完成、已取消） |
| 1_030_000_010 | 「退款单状态已变化，请刷新」 | 审核时退款单已不是待审核，或订单已不是 40 |
| 1_030_000_011 | 「合同已签署，不能重复签名」 | C 端 `POST /contract/sign`，该订单已有签名（2026-11-08 第一期补齐） |

违禁词表（R-2）：`收益、回报、利息、稳赚、返利、回购、分红、年化、投资`，另加文档 12 第 11.4 节列出的承诺类词（词表在 `mock-server/src/compliance.ts`；R-10 的 grep 检查针对页面、种子和产物，不含这张词表）。

---

## 1. 平台端（平台管理员，tenant 1）

### 1.1 租户（芋道自带页面 `system/tenant`，接口按芋道原样）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/system/tenant/page` | 参数 `name`、`status`。返回 `TenantVO[]`：`id, name, contactName, contactMobile, status, websites[], packageId, expireTime, accountCount, createTime` |
| GET | `/admin-api/system/tenant/get?id=` | 单个租户 |
| POST | `/admin-api/system/tenant/create` | 请求 `name, contactName, contactMobile, status, websites, packageId, expireTime, accountCount, username, password`。同时创建该租户的管理员账号和「经营者管理员」角色。返回新租户 id。租户名重复 → 400「租户名已存在」 |
| PUT | `/admin-api/system/tenant/update` | 改名称、联系人、套餐、状态、到期时间 |
| GET | `/admin-api/system/tenant-package/page`、`/get`、`/get-simple-list` | 套餐：`id, name, status, remark, menuIds[], createTime`。三档：基础版 ¥2,988/年、专业版 ¥9,800/年、旗舰版 ¥29,800/年（文档 05） |

### 1.2 风控预警

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/risk-alert/page` | 参数 `tenantId, type, status`。返回 `id, tenantId, tenantName, type, level(1 低 2 中 3 高), title, content, source, status, handleRemark, createTime, handleTime`。只有平台能调，经营者调返回 403 |
| PUT | `/admin-api/farm/risk-alert/handle` | 请求 `id, status(10|20), handleRemark`。`handleRemark` 为空 → 400 |
| GET | `/admin-api/farm/platform/overview` | 平台总览：`tenantCount, activeTenantCount, gmvMonth, orderCountMonth, pendingAlertCount, tenantRank[{tenantId, tenantName, gmvMonth}]` |

## 2. 经营者后台（tenant 2、3 的管理员）

### 2.1 工作台、风险自检

| 方法 | 路径 | 返回 |
|---|---|---|
| GET | `/admin-api/farm/dashboard/summary` | 金额一律分。统计卡：`gmvToday, gmvYesterday, gmvDayOnDayRate`（较昨日 %，昨日为 0 时 null）、`orderCountToday, orderCountYesterday, orderCountMonth, orderCountLast7d, orderCountLast7dRate`（近 7 天较前 7 天 %，null 同上）、`pendingShipCount`（已录入未发货的收成数）、`adoptingMemberCount`（有进行中认养的不同认养人）、`repurchaseRate`（%，已支付订单 ≥2 笔的认养人 / 有已支付订单的认养人）。其余：`gmvMonth, adoptingCount, assetTotal, assetAdopted, pendingRecordCount, pendingServiceOrderCount, overdueSelfTaskCount, riskScore`。曲线：`gmvTrend[{date:'MM-DD', amount}]`（近 30 天，旧版 14 天）、`gmvPeak{date, amount}`。`careModeShare[{mode, name, count}]`（页面已不用，保留）、`slotUsage[{type:'plot'|'animal'|'tree', name, unit, used, total}]`（认养位使用情况）、`latestOrders[{id, no, memberName, productTitle, amount, status, createTime}]`（最新 5 单，含未付款）、`weekOverview{categories[{name, count}]（近 7 天已支付订单按商品分类）, avgOrderAmount, repurchaseRate, refundRate}`（refundRate 暂无退款数据，恒为 null）、`todos[{type, title, count, desc, level('danger'|'primary'|'warning'), path}]`（type：record/service/harvest/trace/risk；level=danger 算「风控提醒」）、`todayRecords[{id, assetCode, title, stewardName, createTime, status}]` |
| GET | `/admin-api/farm/risk/self-check` | `score`（0–100）、`items[{key, name, score, max, status('ok'|'warn'|'bad'), detail, suggestion}]`：资质证照、资金路径、合同签署率、文案合规、溯源完整度；`hits[{productId, productTitle, words[], time}]` 近期被拦截的文案 |

### 2.2 基地

`/admin-api/farm/base/page|get|list|create|update|delete`

字段：`id, name, address, lng, lat, areaMu(亩), certNo(资质编号), cropSummary, status(0 正常 1 停用), assetCount, stewardCount, createTime`。
- `create/update`：`name`、`address` 必填，否则 400。
- `delete`：基地下还有资产 → 400「基地下还有资产，不能删除」。
- `list`：不分页，给下拉框用，返回 `{id, name}`。

### 2.3 资产（地块、畜禽、果树）

`/admin-api/farm/asset/page|get|create|update`

字段：`id, baseId, baseName, assetType, code(如 A-01、耳标号), name, specs{ areaM2, crop, breed, count }, status, cameraName, sensorName, adoptionId, memberName, careMode, createTime`。
- 参数：`baseId, assetType, status, code`。
- `create`：同一基地 `code` 重复 → 400「编号已存在」。
- `GET /admin-api/farm/asset/env?assetId=`：环境数据 `current{ airTemp, airHumidity, soilMoisture, soilTemp, ph, light, updateTime }`、`series[{ ts, airTemp, airHumidity, soilMoisture }]`（近 24 小时，每小时 1 点）。C 端、管家端共用同一个生成逻辑。

### 2.4 认养商品（含三档托管、违禁词拦截）

`/admin-api/farm/product/page|get|create|update|update-status`

字段：
```
id, baseId, baseName, assetType, title, cover, description, cycleDays, areaM2,
basePrice,            // 全托管价，分
stock, sold, status,
careModes: [{
  mode, enabled, price,          // 分；全 128000 / 半 86000 / 不托管 50000
  slogan,                        // 「农场全包，您只管收货」
  outputPromise,                 // 产出说明，受违禁词校验
  contractVersion,               // v3.2 / v2.1 / v1.4
  weeklyTodo,                    // 每周 C 端待办数：0 / 2 / 5
  insurance,                     // 保险说明
  duty: [{ item, owner: 'farm' | 'member' }]   // 13 项农事责任矩阵
}],
createTime
```
- `create/update`：校验 `title`、`description`、每档 `outputPromise`，命中违禁词 → `1_030_000_000`，`data = { words, suggestion }`；`title` 为空 → 400；至少开放一档，否则 400「至少开放一种托管方式」。
- `update-status`：`{ id, status }` 上架、下架。
- `page` 参数 `title, status, baseId` + `assetType`（plot/animal/tree）、`priceMin, priceMax`（分，按 `basePrice` 闭区间过滤）。页签「全部 / 在售 / 已下架」= 不传 / `status=0` / `status=1`。
- 列表派生字段（只读，保存时不用传）：`no`（P + id 补零 3 位，如 P001）、`categoryName`（土地认养 / 家禽认养 / 果树认养）、`totalSlots`（= stock + sold）、`soldRate`（0–100，= sold / totalSlots）、`insured`（任一已开放档位的 `insurance` 含「含」字）、`expectedOutput`（优先全托管档、否则第一个已开放档的 `outputPromise`，口径「约定交付 X 斤，不足部分按同等品质补发」）；另有既有的 `minPrice, enabledModes, baseName`。

### 2.5 认养订单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/order/page` | 参数 `no, memberMobile, careMode, status, productId, createTimeStart, createTimeEnd`（后两个是下单时间区间，毫秒时间戳，含端点）。字段 `id, no, memberId, memberName, memberMobile, productId, productTitle, careMode, amount, status, assetCode, contractVersion, contractAgreed, createTime, payTime`，另有（2026-11-07 新增）`payMethod`（演示固定「微信支付」）、`promisedJin`（约定交付斤数，无则 null）、`harvestedJin`（已收斤数）、`progress`（履约进度 0-100 整数 = 已收 / 约定交付，无约定交付为 0）。页签上的数量由前端按 `status` 各取一次 `total`，不新增接口；`status=40`（退款中）暂无数据，退款闭环未做 |
| GET | `/admin-api/farm/order/get?id=` | 上面的字段 + `contract{ no, version, title, agreed, signedAt }`、`adoption{ id, assetId, assetCode, baseName, startAt, endAt, promisedJin, harvestedJin }`、`records[]`（该认养最近 10 条已通过的农事记录）、`shipments[]`；2026-11-07 新增 `customer{ name, mobile, orderCount, totalAmount }`（累计认养 = 该客户已支付且未取消的订单数；累计消费 = 其金额合计，分）、`steward{ name, mobile(脱敏) }`（没有认养关系时为 null）。履约时间轴由前端用订单、认养、配送的时间拼；资金与分账（服务费 3%、T+1 结算）、交易号、溯源码为前端演示计算，页面标注「演示数据」 |

#### 2.5.1 合同管理（2026-11-07 新增，只读台账，由订单派生）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/contract/page` | 经营者。参数 `no`（合同编号或订单号，模糊，`HT` 前缀自动去掉）、`memberMobile`（模糊）、`status`（10 已签署 / 0 待签署 / 20 已解除）、`pageNo, pageSize`。字段 `id`（同订单 id）, `no`（`HT` + 订单号去掉前两位）, `orderId, orderNo, memberName, memberMobile, productTitle, careMode, careModeName, version, title, status, signMethod`（固定「电子签名」）, `caAuth`, `chainProof`（演示口径：已签署即 true）, `signedAt`（已签署时 = 支付时间，否则 null）, `createTime`。按订单下单时间倒序 |

- 合同状态：订单已取消（status=30）→ 20 已解除；已勾选合同且已支付 → 10 已签署；其余 → 0 待签署。
- 统计卡（总数 / 已签署 / 待签署 / 已解除）由前端按 `status` 各取一次 `total`。合同模板库、反欺诈条款为前端静态演示数据。真实 CA、区块链、原文下载、验真不在本期范围。

### 2.6 农事记录审核

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/record/page` | 参数 `status, assetCode, stewardId`。字段 `id, adoptionId, assetId, assetCode, memberName, stewardId, stewardName, taskId, kind('farming'|'harvest'|'service'), title, content, photos[], lng, lat, env{airTemp, airHumidity, soilMoisture}, checks{ exifTime, gpsFence, duplicate }（布尔，true 表示通过）, status, auditRemark, createTime, auditTime`。派生字段（只读）：`kindName`（农事 / 采收 / 代管服务）、`summary`（「类型名 · 标题」）、`photoCount`、`machineCheck`（`ok` 三项都通过 / `suspect` 存疑）、`machineCheckNote`（存疑原因，「、」连接：拍摄时间偏差 / 地点偏移 / 画面重复率高）。前端状态文案：0 待审核、10 已发布、20 已驳回 |
| PUT | `/admin-api/farm/record/audit` | `{ id, pass: boolean, remark }`。驳回时 `remark` 必填 → 400。只有 `status=0` 能审，否则 `1_030_000_006`。通过后 C 端生长档案可见 |
| GET | `/admin-api/farm/record/stats` | 统计卡：`todayCount`（今日提交）、`pendingCount`（全部待审核）、`autoPassCount`（本月三项校验全通过）、`manualReviewCount`（本月有校验未通过）、`monthCount`（本月累计）、`avgPerAsset`（本月平均每块地条数，1 位小数） |

### 2.7 代管工单

`GET /admin-api/farm/service-order/page|get`：`id, no, fromTaskId, taskTitle, adoptionId, assetCode, memberName, stewardId, stewardName, serviceType, price, status, split{ steward:70, farm:20, platform:10 }（百分比，分账演示阶段不执行）, proofPhotos[], remark, createTime, acceptTime, finishTime`。

`/page` 返回 `{ list, total, stats }`（2026-11-07 对齐原型 08 增加），入参 `status, no`，其余字段不变：

- 行内新增：`careModeName`（认养人托管方式中文名，无认养关系为空串）、`proofCount`（现场照片张数 = `proofPhotos.length`）。`/get` 的 Java 实现同样带这两个字段，mock 的 `/get` 不带（前端用列表行合并）。
- `stats`：`counts{ all, pending, serving, done, cancelled }`（页签数量，对应 status 全部 / 0 / 10 / 20 / 30）、`monthCount`（本月新建工单数）、`monthCountGrowth`（对上月的环比 %，1 位小数；上月为 0 时 `null`）、`income`（本月已完成工单的金额合计，分）、`stewardIncome`（其中管家分成，按 `split.steward` 百分比算，分）、`avgAcceptHours`（已接单工单的 接单时间 − 创建时间 平均小时数，1 位小数；没有则 `null`）、`goodRate`（执行好评率 %，没有评价数据，演示口径固定 `98.4`）。
- 页面文案（取值不变）：`0` 待接单、`10` 服务中（接口枚举叫「已接单」）、`20` 已完成、`30` 已取消。mock：`mock-server/src/farm/stats.ts serviceOrderPage`；Java：`FarmServiceOrderService.page`，返回类型 `FarmFulfillPage`（`list, total, stats`）。

### 2.8 农资商品、农资订单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT | `/admin-api/farm/supply/page|get|create|update` | 字段 `id, name, cat, cover, price, cost, stock, unit, spec, fitModes[], batchNo, retailAllowed, needWorkorder, status, sold, description`。`cat = pesticide` 时服务端强制 `retailAllowed=false, needWorkorder=true` |
| GET | `/admin-api/farm/supply-order/page` | 字段 `id, no, memberName, adoptionId, assetCode, spuId, spuName, cat, qty, amount, deliverWay, batchNo, traceState, createTime` |
| PUT | `/admin-api/farm/supply-order/trace` | `{ id, usedAt, dosage }` 登记施用，`traceState → 10`，并写一条投入品记录 |

2026-11-07 对齐原型 08，`supply/page` 与 `supply-order/page` 返回 `{ list, total, stats }`（mock：`farm/stats.ts supplyPage / supplyOrderPage`；Java：`FarmSupplyService.page` / `FarmSupplyOrderService.page`，返回类型 `FarmFulfillPage`）。以下是新增部分，原有字段不变：

- `supply/page`：入参新增 `batchNo`（批次号模糊匹配）；行内新增只读字段 `code`（编号 `SP-` + (100+id)）、`grossMargin`（毛利率 %，整数，`(price-cost)/price`，售价为 0 时 0）、`stateName`（按顺序判定：`status=1` 已下架 → `needWorkorder` 需工单 → `stock<200` 低库存 → 正常）。`supply/get` 的 Java 实现也带这三个字段。
- `supply/page` 的 `stats`：`monthGmv`（本月农资订单金额合计，分）、`monthOrderCount`（本月农资订单数）、`avgOrderAmount`（客单，分，四舍五入）、`repeatMemberCount`（累计买过 ≥2 单的认养人数）、`repeatRate`（复购率 % = 复购人数 / 下过单的认养人数，1 位小数）、`serviceIncome`（本月已完成代管工单金额合计，分，与 2.7 的 `income` 同口径）、`catCounts`（类目页签数量 `{ all, seed, fert, tool, bio, svc, ins, pesticide }`，没有商品的类目不出现）。
- 页面类目名（取值不变）：`seed` 种子种苗、`fert` 肥料基质、`tool` 农具耗材、`bio` 绿色防控、`svc` 代管服务、`ins` 保险加装、`pesticide` 化学农药。
- `supply-order/page`：入参新增 `keyword`（订单号或认养人昵称模糊匹配）、`assetCode`（绑定地块编号模糊匹配）、`state`（`stateName` 精确匹配），原有 `no`、`traceState` 保留；行内新增 `stateName`。库里没有订单状态字段，`stateName` 是演示口径：`svc` 服务类登记后「已完成」、否则「待派单」；`ins` 保险「已生效」；`tool` 农具登记后「已安装」；其余登记后「已使用」、未登记「待使用」。
- `supply-order/page` 的 `stats`：`todayCount`（今日订单数）、`todayAmount`（今日金额，分）、`pendingDispatch`（`stateName=待派单` 的数量）、`untraced`（`traceState=0` 的数量）、`repeatMemberCount`、`repeatRate`（同上，口径为全部农资订单）。

### 2.9 收成与配送

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/harvest/page` | 字段 `id, adoptionId, assetCode, memberName, crop, weightJin, harvestDate, photos[], remark, shipmentId` |
| POST | `/admin-api/farm/harvest/create` | `{ adoptionId, crop, weightJin, harvestDate, remark }`；`weightJin <= 0` → 400。同时累加认养的 `harvestedJin` |
| GET | `/admin-api/farm/shipment/page` | 字段 `id, no, harvestId, adoptionId, memberName, receiverName, receiverMobile, address, weightJin, carrier, logisticsNo, coldChain, status, tracks[{ time, content }], createTime` |
| POST | `/admin-api/farm/shipment/create` | `{ harvestId, carrier, logisticsNo }`；收成已发过货 → 400。状态直接到「运输中」，自动生成两条轨迹 |

2026-11-07 对齐原型 08（mock：`farm/stats.ts harvestPage / shipmentPage` 与 `farm-admin.ts harvest/create`；Java：`FarmHarvestService`、`FarmShipmentService`，page 返回类型 `FarmFulfillPage`）：

- `harvest/page` 返回 `{ list, total, stats }`，`stats`：`totalCount`（累计批数）、`totalWeightJin`（累计斤数）、`monthWeightJin`（本月采收斤数）、`unshippedCount`、`unshippedWeightJin`（未发货批数、斤数），斤数均保留 1 位小数。
- `harvest/create` 入参新增 `items: [{ crop, weightJin }]`（收获明细，可多品类）和 `farmType`（农事类型：播种 / 施肥 / 病虫害防治 / 浇水 / 搭架整枝 / 采摘采收 / 翻耕，不传按「采摘采收」）。传了 `items` 就以它为准，老的 `crop + weightJin` 仍兼容。不新增表字段：`crop` 存各品类名用「、」连接（≤64 字），`weightJin` 存合计，多品类时明细追加进 `remark`（格式 `作业说明；明细：番茄 12.4 斤、黄瓜 6.2 斤`，≤255 字）；超长 → 400。每行品类为空或重量 ≤0 → 400（沿用「请填写作物」「重量要大于 0」）。`farmType` 写进生长档案内容（`…录入收成（农事类型：采摘采收），称重 X 斤。`）。
- `shipment/page` 返回 `{ list, total, stats }`，行内新增 `batchNo`（`B` + 创建日期，如 `B2026-1107`，同一天发出的配送单算一个批次；订单详情用的 `toViews` 也带）、`planDate`（创建当天 0 点毫秒时间戳）、`progress`（完成度 %：待发货 28、已发货 62、已完成 100）。仍是「一单一人」的配送单模型，没有改成原型的「批次」实体。`stats`：`counts{ all, pending, shipped, done }`（页签数量，对应 status 全部 / 0 / 10 / 20）、`batchCount`（批次数）、`shippedWeightJin`（已发货在途斤数）、`coldChainCount`（冷链单数）。
- 页面文案（取值不变）：配送状态 `0` 待发货、`10` 已发货（接口枚举叫「运输中」）、`20` 已完成（接口枚举叫「已签收」）。

### 2.10 问题反馈（R-12，平台和经营者共用）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin-api/farm/feedback/create` | `{ type, content, screenshots[] (data URL 或文件地址，≤3), pageTitle, pagePath, userAgent }`；内容为空 → 400「请填写反馈内容」；截图超过 3 张 → 400「最多上传 3 张截图」。返回 `{ id, no: 'FB-0001' }`，编号全局递增 |
| GET | `/admin-api/farm/feedback/page` | 参数 `type, status, no`。平台看全部租户，经营者只看自己租户的。字段 `id, no, tenantId, tenantName, type, content, screenshots[], pageTitle, pagePath, userAgent, creatorName, status, replies[{ id, content, creatorName, createTime }], createTime, updateTime` |
| GET | `/admin-api/farm/feedback/get?id=` | 单条 |
| POST | `/admin-api/farm/feedback/reply` | `{ id, content }`，仅平台；内容为空 → 400 |
| PUT | `/admin-api/farm/feedback/update-status` | `{ id, status }`，仅平台 |

### 2.11 文章（R-11，芋道商城自带接口原样）

`/admin-api/promotion/article/page|get|create|update|delete`、`/admin-api/promotion/article-category/page|get|create|update|delete|list-all-simple`。
- 文章字段按芋道：`id, categoryId, title, author, picUrl, introduction, content, sort, status, browseCount, recommendHot, recommendBanner, spuId, createTime`。
- 按租户隔离；`title` 为空 → 400「标题不能为空」。

### 2.12 文件上传

`POST /admin-api/infra/file/upload`、`POST /app-api/infra/file/upload`（multipart，字段 `file`，≤5MB）→ `data` 是可直接放进 `<img src>` 的地址。演示阶段返回 data URL，不落盘；重置后清空。

## 3. 管家端（kind = steward 的后台账号，`/admin-api/farm/steward/**`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/farm/steward/home` | `stewardName, baseName, todo{ taskCount, overdueCount, serviceOrderPending, serviceOrderMine }, todayDone, monthDone, pendingAuditCount（自己提交待审核的记录数）, rejectedCount（近 7 天被驳回数）` |
| GET | `/farm/steward/task/list` | 参数 `status`（默认 0）。只返回 `stewardId` 是自己的任务：`id, assetId, assetCode, baseName, memberName, careMode, title, description, source('calendar'|'iot'|'manual'|'service'), serviceOrderId, deadline, status, overdue（待办且已过截止时间）, finishTime` |
| GET | `/farm/steward/task/get?id=` | 任务 + `asset{ code, crop, areaM2 }` + `env`（当前环境快照） |
| POST | `/farm/steward/task/checkin` | `{ taskId, content, photos[] (≥1), lng, lat }`。照片为空 → 400「至少拍一张现场照片」；任务不是自己的 → `1_030_000_007`；已完成 → `1_030_000_006`。生成一条待审核的农事记录（自动带环境快照、三项校验结果），任务置完成。来自代管工单的任务，工单同时置完成 |
| GET | `/farm/steward/service-order/list` | 参数 `scope=pool|mine`：`pool` 是本基地待接单的，`mine` 是自己已接的 |
| PUT | `/farm/steward/service-order/accept` | `{ id }`，待接单 → 已接单，并给自己生成一条来源为 `service` 的任务；已被接 → `1_030_000_006` |
| GET | `/farm/steward/asset/get?id=` | 标的详情：资产、认养人（姓名、手机号脱敏）、托管方式、`env`、最近 5 条记录、本周的自管任务完成情况 |

## 4. C 端（`/app-api/farm/**`，会员登录；浏览接口不需要登录）

| 方法 | 路径 | 登录 | 说明 |
|---|---|---|---|
| GET | `/farm/home` | 否 | `banners[{ image, title, url }]`、`bases[{ id, name, cover, live: boolean }]`、`products[]`（上架商品摘要：`id, title, cover, minPrice, basePrice, sold, stock, careModes[mode]`）、`notices[{ title }]` |
| GET | `/farm/product/get?id=` | 否 | 和后台 2.4 字段一致，另加 `base{ name, address, certNo }`、`compareRows[{ item, full, semi, self }]`（托管对比） |
| POST | `/farm/order/create` | 是 | `{ productId, careMode, contractAgreed, receiverName, receiverMobile, address, deliverWay }`（`deliverWay`：`cold` 冷链快递到家 / `pickup` 基地自提 / `monthly` 月度集中配送，演示口径，只接收不落库）。未勾选合同 → `1_030_000_001`；商品下架/该档未开放 → 400。返回 `{ id, no, amount }`，状态待付款 |
| POST | `/farm/order/pay` | 是 | `{ id }` 模拟支付：状态 → 认养中，绑定一个空闲资产，生成认养关系；按托管方式生成自管任务（全托管 0 项、半托管 2 项、不托管 5 项）和管家任务。返回 `{ orderId, adoptionId }`。错误 `1_030_000_002`、`1_030_000_003` |
| GET | `/farm/order/get?id=` | 是 | 订单字段（同 2.7 订单，含 `payMethod`、`promisedJin`、`harvestedJin`、`progress`）+ `contract{ no, version, title, agreed, signedAt }`，2026-11-07 新增认养摘要（未支付时 `adoptionNo/adoptionStatus/startAt/endAt/areaM2/insuranceNo/traceCode` 为 null，`shipmentCount` 为 0，`assetName/stewardName/stewardMobile` 为空串）：`adoptionNo`（演示口径 `A{年}-{认养 id 补 4 位}`）、`adoptionStatus`、`startAt`、`endAt`（服务周期）、`shipmentCount`（该认养已有配送单数）、`assetName`、`areaM2`、`stewardName`、`stewardMobile`（脱敏）、`insuranceNo`（演示保单号 `PICC-{年}-{订单 id 补 4 位}`）、`traceCode`（演示溯源码 `TR`+订单号去掉前两位）。用于支付结果页 |
| GET | `/farm/order/list` | 是 | 我的订单，新的在前，每条字段同 `/farm/order/get`（含 `contract` 和认养摘要）。「我的订单」页（页签前端按 `status` 过滤：0 待付款 / 10 认养中 / 20 已完成）和「合约中心」（取 `contract.signedAt` 非空的）共用 |
| GET | `/farm/adoption/list` | 是 | 我的认养：`id, orderId, productTitle, cover, careMode, assetCode, baseName, startAt, endAt, status, promisedJin, harvestedJin, selfTodoCount` |
| GET | `/farm/adoption/get?id=` | 是 | 上面的字段 + `asset{ code, crop, areaM2, cameraName }`、`steward{ name, mobile(脱敏) }`、`careModeInfo`（该档的标语、责任、产出说明、合同版本） |
| GET | `/farm/adoption/records?adoptionId=` | 是 | 生长档案：已通过的农事记录，倒序 |
| GET | `/farm/adoption/trace?adoptionId=` | 是 | 溯源六段 `stages[{ key, title, time, content }]`（种源、播种、生长、投入品、采收、配送）和 `inputs[{ source('farm'|'member'), name, batchNo, usedAt, dosage }]` |
| GET | `/farm/adoption/env?adoptionId=` | 是 | 同 2.3 env |
| GET | `/farm/adoption/camera?adoptionId=` | 是 | `{ name, online, poster, tip: '演示环境为视频占位，正式版接入萤石/海康直播流' }` |
| GET | `/farm/self-task/list?adoptionId=&status=` | 是 | 自管任务：`id, title, description, deadline, status, overdue, entrustPrice` |
| POST | `/farm/self-task/done` | 是 | `{ id }`；状态不是待办 → `1_030_000_006` |
| POST | `/farm/self-task/entrust` | 是 | `{ id }` 委托管家代办：生成一张待接单的代管工单（价格取 `entrustPrice`），任务状态 → 已委托（R-5）。返回 `{ serviceOrderId, no }` |
| GET | `/farm/supply/list?cat=` | 否 | 只返回 `retailAllowed=true` 且上架的 |
| GET | `/farm/supply/get?id=` | 否 | 单个 |
| POST | `/farm/supply-order/create` | 是 | `{ spuId, qty, adoptionId, deliverWay }`。没有 `adoptionId` → `1_030_000_004`；`retailAllowed=false` → `1_030_000_005`；`qty < 1` 或超库存 → 400。生成后 `traceState=0` |
| GET | `/farm/supply-order/list` | 是 | 我的农资 |
| GET | `/farm/shipment/list?adoptionId=` | 是 | 收获配送：同 2.9 shipment 字段 |
| POST | `/farm/shipment/confirm` | 是 | `{ id }` 确认收货（认养详情「收获配送」页签）。只有属于当前会员的配送单；不是自己的/不存在 → 404；状态不是运输中（10）→ `1_030_000_002`「只有运输中的配送单能确认签收」。成功后状态 → 已签收（20），追加一条轨迹「已签收，签收人：xxx」，与后台 `PUT /shipment/sign` 逻辑一致。返回 `true` |

## 5. 不在契约里的

- 分账、真实支付、电子合同签署、直播流、IoT 设备接入：页面用演示数据代替并标出「演示数据」。
- 优惠券、积分、分销、社区。（退款闭环第二期已做，见第 6.1 节；真实原路退款仍不做，只改状态。）

## 6. 第二期新增接口（2026-11-07 汇总）

来源：`docs/plans/第二期-登记/{A,B,C端,管家端}.md`，路由以 `mock-server/src/routes/farm-{refund,people,ext-b,app-extra,steward-extra}.ts` 核对，路径与登记一致，差异写在各节备注。金额单位分，时间毫秒时间戳；后台、管家端前缀 `/admin-api/farm`，C 端前缀 `/app-api/farm`；分页接口返回 `{ list, total, stats }`。Java 未编译、登录后页面未验证，以服务器实测为准 [未验证]。

### 6.1 退款（A）

状态联动：申请时订单 `10 -> 40`；驳回时退款单 `0 -> 20`、订单回到申请前状态；通过时退款单 `0 -> 10`、订单 `40 -> 50`、认养 `10 -> 30`、地块 `10 -> 0`、待办任务 `0 -> 30`、待接单代管工单 `0 -> 30`，合同台账显示「已解除」。款项原路退回不做，只改状态。试算：已过周期比例 × 实付 × 20%（演示系数）为投入扣减，已收斤数 / 约定斤数折算为交付扣减。

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/admin-api/farm/refund/page` | `no, orderNo, memberMobile`（模糊）、`status, pageNo, pageSize` | 退款单：`id, no, orderId, orderNo, orderStatus, memberName, memberMobile, productTitle, assetCode, careMode, amount, investDeduct, deliverDeduct, deduct, refundAmount, reason, status, statusName, auditRemark, prevOrderStatus, createTime, auditTime` | 经营者；新的在前 |
| GET `/admin-api/farm/refund/stats` | - | `counts{all,0,10,20}, monthApply, refundRate, pending, pendingOver24h, avgHandleHours, refundedAmount, refundedCount, reasonAlert` | `refundRate` = 本月申请数 / 本月已付订单数（%），分母 0 为 null |
| GET `/admin-api/farm/refund/get?id=` | `id` | 退款单字段 + `adoption{id,startAt,endAt,promisedJin,harvestedJin}`（可空）、`elapsedRate` | 不存在 404 |
| PUT `/admin-api/farm/refund/audit` | `{ id, approve, remark? }` | 审核后的退款单 | 驳回 `remark` 必填（≤200）否则 400；已审核或订单状态变 `1_030_000_010` |
| POST `/app-api/farm/refund/create` | `{ orderId, reason }`（≤200） | 退款单（`status=0`） | 原因空 400；别人的订单 404；`1_030_000_008`、`1_030_000_009`；驳回后可再次申请 |
| GET `/app-api/farm/refund/list` | - | 我的退款单数组，字段同上 | 新的在前 |
| GET `/app-api/farm/refund/preview?orderId=` | `orderId` | `orderId, orderNo, canApply, orderStatus, amount, elapsedRate, investDeduct, deliverDeduct, deduct, refundAmount` | 退款页试算；别人的订单 404 |

备注：C 端登记（C端.md）按 0 待审核 / 10 已同意 / 20 已退款 / 30 已驳回四档显示退款状态，与 A 和 mock 的三档（0 / 10 已通过 / 20 已驳回）不一致，以 A 和 mock 为准，C 端页面需对齐 [待对齐]。订单 `status` 会出现 40/50，C 端订单为 50 时 `adoptionStatus=30`；2.5 合同台账 50 按「已解除」（`contractStatusOf` 需同步，主线程跟进项）。

### 6.2 客户、分层、管家管理（A，全部只读，经营者）

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/customer/page` | `keyword, city, tag, tier(core/growth/new/sleep/other), productId, activeDays, minAmount, maxAmount, expiring=1, pageNo, pageSize` | `id, name, mobile(脱敏), city, level(V1-V4), adoptCount, totalAmount, tags[], lastActive, tier, tierName, renewIntent, activeAdoptionCount, expiringCount` | 按最近活跃倒序；已付订单 = 有支付时间且状态 ∈ {10,20,40}，已退款/已取消不计；V1-V4 按累计消费 <1000/<3000/<8000 元，演示口径 |
| GET `/customer/stats` | - | `total, newThisMonth, adopting, expiring30, repeatRate, avgOrderAmount, cities[], tags[]` | - |
| GET `/customer-tier/summary` | - | `total, tiers[{key,name,level,levelType,rule,action,count}], expiring{customerCount,adoptionCount,estimateAmount,remindDays}, packs[], updatedAt` | RFM 规则圈选；人群包只读，新建、推送、续费券不做 |
| GET `/steward-manage/page` | `keyword, baseId` | `id, name, mobile(脱敏), roleName, baseId, baseName, assetTotal, assetAdopted, memberCount, monthTaskTotal, monthTaskDone, monthTaskRate, avgResponseMinutes, status` | 前缀 `steward-manage`，不是管家端的 `steward`；在岗状态恒 0 |
| GET `/steward-manage/overview` | - | `ranking[{id,name,score}], today{pendingTasks,pendingRecords,pendingServiceOrders}` | - |
| GET `/steward-manage/get?id=` | `id` | 同 page 行 | mock 有、登记未写；非本租户管家 404 |

### 6.3 交易流水、分账、经营分析（B）

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/finance/flow/page` | `pageNo, pageSize, tradeNo, memberName, type(adopt/supply/service), settleStatus(settled/settling), beginTime, endTime` | 行 `tradeNo, memberName, type, typeName, amount, fee, net, settleStatus, settleStatusName, time, refNo`；stats `todayAmount/Count, last30Amount/Count/Growth, last30Fee, feeRate, pendingAmount/Count` | 由已付未取消的认养订单 + 农资订单 + 已完成代管工单派生；服务费 3%、T+1 为演示口径；统计为滚动 30 天且不受筛选影响；退款流水未接入 |
| GET `/finance/split/get` | - | `demo, exampleAmount, rules[{key,name,type,percent,exampleAmount}], account, month{label,total,items[]}, serviceSplit{count,total,steward,farm,platform}, invoices[], feeRate` | 规则 88/3/6/3 为演示；`serviceSplit` 取代管工单的真实比例 |
| GET `/analysis/summary` | `days=7/30/90`（默认 30） | `funnel[{name,value,rate,demo}], channels[], categories[], retention[], quality[], paidCount` | 下单、支付、签约为订单实算；曝光、浏览、首季续费、渠道、留存为演示（`demo=true`） |

### 6.4 资质证照、资金公示（B）

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/cert/page` | `pageNo, pageSize, name, type(必备/建议/按需)` | 行 `verify(verified/pending/renew/expired), verifyName, daysLeft, expiringSoon, publicMode, publicName, editable, source(upload/base), demo`；stats `total, expiringSoon, expired, missingRequired, records[]` | 基地备案证（id≥9000）由基地资料派生，只读 |
| POST `/cert/create` | `name, no, type, validUntil(ms 或 null), publicMode, fileUrl` | 新 id | 状态固定「待核验」；名称/编号空、类型不在三值内 400；无删除、无平台核验 |
| PUT `/cert/update-public` | `id, publicMode` | - | 基地备案证 400「来自基地资料」 |
| GET `/fund-notice/get` | - | `fields[{key,label,recommend,enabled}], preview{payeeName,channelName,feeRate,policyNo,monthlyTotal,memberTotal,steps}, usage[], usageTotal, updateTime` | 用途占比为演示，仅平台服务费一行真实 |
| PUT `/fund-notice/save` | `fields:{flowChart,payeeName,channelName,feeRate,policyNo,monthlyTotal,memberTotal}`（boolean） | - | 只更新传了 boolean 的键；C 端「合规说明」读取接口未做 |

### 6.5 IoT 设备、视频监控（B）

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/device/page` | `pageNo, pageSize, code, kind(camera/sensor), status(online/offline)` | 行 `code, kind, typeName, position, bindText, assetCodes, status, statusName, lastReportTime, firmware, dispatched, custom`；stats `total, cameraCount, sensorCount, onlineCount, offlineCount, todayReports, retainDays, storage*` | 设备由资产的 `cameraName/sensorName` 去重派生；在线状态、上报时间、固件为演示值 |
| POST `/device/create` | `kind, typeName, name, position, bindText` | 设备编号 | 名称空 400 |
| PUT `/device/update` | `code, position, firmware` | - | 找不到 404 |
| PUT `/device/dispatch` | `code` | - | 非离线设备 400「只有离线设备可以派单」 |
| GET `/camera/overview` | - | `demo, stats{cameraCount,onlineCount,offlineCount,todayViews,watchHours,retainDays,alertCount}, cameras[], policy[]` | 画面为占位；`visibleMembers` 真实，`todayViews` 演示 |

### 6.6 托管模式配置（B）

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/hosting-config/get` | `productId`（缺省取本租户第一个商品） | `product, products[], modes[3]{mode,name,configured,enabled,price,priceDelta,slogan,outputPromise,contractVersion,weeklyTodo,insurance,duty,dutyFarmCount,dutyMemberCount,supplyPackage[]}, dutyMatrix[], switchRule, trigger{overdueIncome,overdueCount}, compliance{pesticideBlocked,rules[]}` | 只读；保存走 2.4 的 `PUT /product/update`（传 `id + careModes`），违禁词校验同在那里；沿用「约定交付 X 斤，不足部分按同等品质补发」，不用原型的「保底」 |

### 6.7 消息模板（B）

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/msg-template/page` | `pageNo, pageSize, name, status(0 启用/1 停用)` | `id, name, trigger, channels[], content, status` | - |
| POST `/msg-template/create`、PUT `/msg-template/update` | `name, trigger, channels(订阅消息/短信/App 推送), content`（update 另传 `id`，未传字段沿用） | 新 id / true | 名称或内容命中违禁词 `1_030_000_000`，`data{words,suggestion}` |
| PUT `/msg-template/update-status` | `id, status` | - | - |
| POST `/msg-template/test-send` | `id` | `{ sent:false, demo:true, channels, preview }` | 不真实下发；已停用 400 |

### 6.8 C 端新增接口（`/app-api/farm`）

退款相关见 6.1。数据存 `farm_app_extra` 一张表，按 `kind` 区分（收藏、地址、评价、到访、转赠、聊天、消息已读）。

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/search/hot`（免登录） | - | `string[]` | - |
| GET `/search`（免登录） | `keyword, type(plot/animal/tree), sort(default/sold/price)` | `{ products[], bases[] }` | mock 的 products 为完整商品视图，字段是登记的超集 |
| GET `/base/list`、`/base/get?id=`（免登录） | `id` | 基地卡片 `id, name, cover, address, areaMu, cropSummary, certNo, productCount, rating, reviewCount`；get 另有 `lng, lat, products[], certs[], news[], reviews[]` | 无评价时 `rating=null` |
| GET `/favorite/list`、GET `/favorite/check`、POST `/favorite/toggle` | `type(product/base), targetId` | 列表 `[{id,type,targetId,product/base}]`；check、toggle 返回 boolean | - |
| GET `/address/list`、POST `/address/save`、POST `/address/delete` | save `{id?,name,mobile,detail,isDefault}`；delete `{id}` | 列表 `[{id,name,mobile,detail,isDefault}]`；save 返回 id | 收货人、地址必填，手机号 11 位，否则 400；无地址时用会员资料带出一条 `id=0` |
| GET `/message/list`、POST `/message/read` | list `type(system/logistics/farming/interact)`；read `{key?}`（不传=全部已读） | `{ unread, list:[{key,type,title,content,createTime,link,read}] }` | 由订单、配送、农事记录、管家回复、转赠派生 |
| GET `/record/get?id=`、`/report/get?adoptionId=`、`/logistics/get?id=` | `id` / `adoptionId` | 记录详情（含 `env, checks, evidenceNo, workHours=null`）；质检报告（`demo=true`）；配送单 + `nth, items[], steward` | 只能看自己的，否则 404；质检报告、存证编号为演示数据 |
| GET `/transfer/list`、POST `/transfer/create` | `{ adoptionId, toMobile, scope(all/right), message? }` | `{ id, no }` | 只记录申请，不改归属；非认养中、已有待确认转赠 `1_030_000_002` |
| GET `/review/list`（免登录）、GET `/review/mine`、POST `/review/create` | create `{ adoptionId, score(1-5), dims{freshness,service,logistics}, content? }` | 新 id | 同一认养只评一次，重复 `1_030_000_002`；别人的认养 404 |
| GET `/visit/list`、POST `/visit/create` | `{ baseId, date(YYYY-MM-DD), people(1-20), meal, items[], remark? }` | `{ id, no }` | 日期早于今天 400 |
| GET `/service/messages`、POST `/service/send` | `{ content }` | 聊天记录 | 发送后自动追加一条演示回复 |

### 6.9 管家端新增接口（`/admin-api/farm/steward`，管家账号）

只能操作 `adoption.stewardId` 是当前账号的认养；非本人负责 `1_030_000_007`；非管家账号 403。mock 的异常上报、消息、自管提醒数据存内存，重启清空；Java 落 `farm_steward_alarm / message / remind` 三张表。

| 方法 路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET `/shipment/list` | `status`（0/10/20） | `{ stats{pending,shipped,done}, pending[{harvestId,crop,weightJin,harvestDate,assetCode,memberName}], list[] }` | `status` 非 0 时 `pending` 为空，=0 时 `list` 为空 |
| POST `/shipment/create` | `harvestId, carrier, logisticsNo, coldChain?` | 配送单 id | 承运商空 400「请填写承运商」；口径同后台建单，多一层归属校验 |
| PUT `/shipment/sign` | `id` 或 `no`（扫码单号） | true | 自提核销、签收；非运输中 `1_030_000_002`「只有运输中的配送单能确认签收」 |
| GET `/self-guide/list` | `state`(todo/reminded/over，默认 todo) | `{ stats{plots,weekTodo,reminded,overdue}, counts, list[{taskId,assetCode,memberId,memberName,careModeName,title,deadline,state,entrustPrice,remindedAt}] }` | 数据为名下 `owner=member` 的自管任务 |
| POST `/self-guide/remind` | `taskId, entrust?` | true | 记提醒并给认养人发聊天消息；`entrust=true` 只发消息，不自动建工单；任务非待办 `1_030_000_006` |
| GET `/record/list` | `status` | `{ stats{all,pending,published,rejected}, list[] }` | 我提交的，最多 100 条 |
| GET `/asset/list` | `scope`(all/raising/free) | `{ counts, list[{id,code,name,assetType,crop,areaM2,raising,statusName,memberName,mine,progress,harvestedJin,promisedJin,leftDays}] }` | 范围 = 所在基地的标的 |
| GET `/supply-task/list` | `tab`(todo/done) | `{ counts{todo,done}, list[{id,no,assetCode,memberName,supplyName,cat,batchNo,kind,hint,done,usedAt,dosage}] }` | 排除保险(ins)、服务(svc)类 |
| PUT `/supply-task/apply` | `id, dosage, usedAt?` | true | 写投入品台账（source=farm），口径同后台 `PUT /supply-order/trace`；已登记 `1_030_000_006`「已经登记过了」 |
| GET `/alarm/list` | - | `[{id,type,level,assetCode,title,content,qty,photos,insuranceReported,createTime}]` | 我上报的 |
| POST `/alarm/create` | `type(设备异常/损耗登记/气象灾害/病虫害爆发), content, assetId?, qty?, photos?` | id | 非「设备异常」必须有照片；损耗必须填数量；保险报备仅演示标记 |
| GET `/message/conversations` | - | `{ list[{memberId,memberName,assetCode,last,lastTime,unread}], notices }` | 名下在养认养人 |
| GET `/message/list` | `memberId` | `{ member{id,name,mobile(脱敏),assetCode}, list[{id,from,content,createTime}] }` | 非名下认养人 `1_030_000_007` |
| POST `/message/send` | `memberId, content`（1-500 字） | true | 同上 |
| GET `/performance` | - | `{ month, assetCount, memberCount, monthCheckins, completionRate, passRate, outputRate, responseMinutes, satisfaction, score }` | `responseMinutes, satisfaction, score` 无数据恒 null，页面显示「暂无数据」 |

## 7. 第一期补齐（2026-11-08，方案见 docs/plans/第一期补齐-2026-11-08.md）

admin = `/admin-api/farm`，app = `/app-api/farm`；响应均 `{code,data,msg}`；状态不对、参数不对统一 `400`，不存在 `404`。时间一律毫秒时间戳，金额分。[未验证：Java 仅编译，登录后页面未走]

### 7.1 订单补字段（farm_order 加列）
| 方法路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| POST app `/order/create`（变更） | 原入参 + 可选 `deliverWay`(express 快递到家/pickup 到基地自提/farm_deliver 基地配送，缺省 express)、`channel`(mini/h5/scan/steward，缺省 h5)、`packageName`(≤64，缺省「托管方式 · 一年」)、`lng`(-180~180)、`lat`(-90~90)、`gpsText`(≤128) | `{id,no,amount}` | 不认识的 deliverWay/channel、坐标越界 → 400；旧版前端传 cold/monthly 会被拒，须改成新取值 |
| GET admin `/order/page`（变更） | 同前 | 行增加 `channel, deliverWay, packageName, couponName, couponAmount, gpsLng, gpsLat, gpsText` | 老订单缺省 channel=h5、deliverWay=express、couponAmount=0 |
| GET admin `/order/get`（变更） | `id` | 在原字段上增加：上述订单字段 + `customerTags: string[]`（新客/多单客户/全托管用户/有退款记录 + 分层名，由客户分层派生不落库）、`contract.signedAt`（手写签名时间，无签名沿用支付时间）、`contract.hasSignature` | 订单视图不返回签名图片；couponName/couponAmount 目前只有演示种子，下单不产生优惠 |
| GET app `/order/get` `/order/list`（变更） | 同前 | 同订单视图增加上述字段 + `contract.hasSignature` | |

### 7.2 配送批次（farm_ship_batch；farm_shipment 加 batch_id）
| 方法路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET admin `/ship-batch/page` | `pageNo,pageSize,status(0待发/10已发出/20已完成),no` | `{list[{id,no(PC+日期+序号),harvestId,planDate,status,remark,createTime,harvestCrop,assetCode,shipmentCount,weightJin}],total}` | |
| GET admin `/ship-batch/get` | `id` | 同上 + `shipments[]`（配送单视图） | 不存在 404 |
| POST admin `/ship-batch/create` | `{harvestId, planDate, remark?(≤255), shipmentIds[]}` | id | 收成/配送单不存在、配送单为空/重复、已在其它批次、planDate 缺失 → 400 |
| PUT admin `/ship-batch/ship` | `{id}` | true | 只允许 status=0（否则 400）；批次下待发配送单置 10 并追加轨迹 |
| PUT admin `/ship-batch/finish` | `{id}` | true | 只允许 status=10（否则 400） |

### 7.3 收成环境快照（farm_harvest.env_snapshot，text JSON）
| 方法路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| POST admin `/harvest/create`（变更） | 同前 | id | 创建时按该资产此刻的环境数据写快照 |
| GET admin `/harvest/page`（变更） | 同前 | 行增加 `envSnapshot{temp,humidity,soilMoisture,light,capturedAt,source:'演示数据'}` | 老数据按采收日的环境数据补，`source` 恒为「演示数据」（IoT 为演示口径） |
| GET app `/harvest/list`（新增） | `adoptionId?` | 同上行（含 `envSnapshot`） | 仅自己认养下的收成 |
| GET app `/harvest/get`（新增） | `id` | 同上 | 别人的/不存在 404 |

### 7.4 畜禽批次（farm_livestock_batch）
| 方法路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET admin `/livestock-batch/page` | `pageNo,pageSize,species(模糊),status(0在栏/10出栏中/20已出栏),baseId` | `{list[{id,no(XB+日期+序号),baseId,baseName,species,breed,inDate,count,outCount,remaining,status,pen,remark,createTime}],total}` | |
| GET admin `/livestock-batch/get` | `id` | 同上一行 | 404 |
| POST admin `/livestock-batch/create` | `{baseId,species(≤32),breed?,inDate?(缺省今天),count(正整数),pen?,remark?}` | id | 基地不是本租户/种类为空/数量非法 → 400 |
| PUT admin `/livestock-batch/update` | 同上 + `id` | true | `count` 不得小于已出栏数；有出栏时状态按新数量重算 |
| PUT admin `/livestock-batch/out` | `{id,count}` | true | out_count 累加，超出剩余 → 400；满额置 20，否则 10 |
| DELETE admin `/livestock-batch/delete` | `id` | true | 仅 out_count=0，否则 400 |

### 7.5 合同签名（farm_order.signature text、signed_at）
| 方法路径 | 入参 | 出参关键字段 | 备注/错误 |
|---|---|---|---|
| GET app `/contract/get` | `orderId` | `{id,no,orderId,orderNo,title,version,careMode,careModeName,status,productTitle,amount,memberName,createTime,payTime,signed,signedAt,hasSignature,signature(dataURL 或 null)}` | 只能看自己的，否则 404；合同编号=订单编号 |
| POST app `/contract/sign` | `{contractId 或 orderId, signature}`：PNG 的 base64 或 `data:image/png;base64,…`，解码后 ≤200KB | `{orderId,signedAt}` | 非本人 404；已解除合同 400；非 PNG/格式错/超 200KB/为空 400；已签 `1_030_000_011` |
| GET admin `/contract/page`（变更） | 同前 | 行增加 `hasSignature`；`signedAt` 优先取签名时间 | 后台不返回签名图片本身 |

### 7.6 租户切换
平台管理员（tenantId=1 的后台账号）请求头带 `visit-tenant-id: <租户编号>`，后续 admin 请求按所选租户查看；其它账号带该头无效（mock）。Java 沿用芋道 `TenantVisitContextInterceptor`，需账号有 `system:tenant:visit` 权限，无权限返回 403「您无权切换租户」；切换期间 `FarmPlatformGuard.isPlatform()` 按所选租户判断。

### 7.7 差异与未做
- mock 单号序号 3 位，Java（`FarmNoGenerator`）4 位。
- 菜单（发布认养商品、配送批次、畜禽批次，权限串 `farm:shipbatch:*`、`farm:livestockbatch:*`）与按钮权限属 web-admin/菜单种子，不在本节。
