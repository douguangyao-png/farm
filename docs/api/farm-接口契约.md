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
| 认养订单状态 `order.status` | `0` 待支付、`10` 认养中、`20` 已完成、`30` 已取消 |
| 农事记录状态 `record.status` | `0` 待审核、`10` 已通过、`20` 已驳回 |
| 农事任务状态 `task.status` | `0` 待办、`10` 已完成、`20` 已委托代办 |
| 任务执行方 `task.owner` | `steward` 管家、`member` 认养人（自管任务） |
| 代管工单状态 `serviceOrder.status` | `0` 待接单、`10` 已接单、`20` 已完成、`30` 已取消 |
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
| 1_030_000_007 | 「只能处理分配给自己的任务」 | 管家操作别的管家的任务 |

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
| GET | `/admin-api/farm/dashboard/summary` | `gmvToday, gmvMonth, orderCountMonth, adoptingCount, assetTotal, assetAdopted, pendingRecordCount, pendingServiceOrderCount, overdueSelfTaskCount, riskScore, gmvTrend[{date:'MM-DD', amount}]`（近 14 天）、`careModeShare[{mode, count}]`、`todos[{type, title, count, path}]`、`todayRecords[{id, assetCode, title, stewardName, createTime}]` |
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

### 2.5 认养订单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/order/page` | 参数 `no, memberMobile, careMode, status, productId`。字段 `id, no, memberId, memberName, memberMobile, productId, productTitle, careMode, amount, status, assetCode, contractVersion, createTime, payTime` |
| GET | `/admin-api/farm/order/get?id=` | 上面的字段 + `contract{ no, version, title, signedAt }`、`adoption{ id, assetId, assetCode, baseName, startAt, endAt, promisedJin, harvestedJin }`、`records[]`（该认养最近 10 条已通过的农事记录）、`shipments[]` |

### 2.6 农事记录审核

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/record/page` | 参数 `status, assetCode, stewardId`。字段 `id, adoptionId, assetId, assetCode, memberName, stewardId, stewardName, taskId, kind('farming'|'harvest'|'service'), title, content, photos[], lng, lat, env{airTemp, airHumidity, soilMoisture}, checks{ exifTime, gpsFence, duplicate }（布尔，true 表示通过）, status, auditRemark, createTime, auditTime` |
| PUT | `/admin-api/farm/record/audit` | `{ id, pass: boolean, remark }`。驳回时 `remark` 必填 → 400。只有 `status=0` 能审，否则 `1_030_000_006`。通过后 C 端生长档案可见 |

### 2.7 代管工单

`GET /admin-api/farm/service-order/page|get`：`id, no, fromTaskId, taskTitle, adoptionId, assetCode, memberName, stewardId, stewardName, serviceType, price, status, split{ steward:70, farm:20, platform:10 }（百分比，分账演示阶段不执行）, proofPhotos[], remark, createTime, acceptTime, finishTime`。

### 2.8 农资商品、农资订单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT | `/admin-api/farm/supply/page|get|create|update` | 字段 `id, name, cat, cover, price, cost, stock, unit, spec, fitModes[], batchNo, retailAllowed, needWorkorder, status, sold, description`。`cat = pesticide` 时服务端强制 `retailAllowed=false, needWorkorder=true` |
| GET | `/admin-api/farm/supply-order/page` | 字段 `id, no, memberName, adoptionId, assetCode, spuId, spuName, cat, qty, amount, deliverWay, batchNo, traceState, createTime` |
| PUT | `/admin-api/farm/supply-order/trace` | `{ id, usedAt, dosage }` 登记施用，`traceState → 10`，并写一条投入品记录 |

### 2.9 收成与配送

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin-api/farm/harvest/page` | 字段 `id, adoptionId, assetCode, memberName, crop, weightJin, harvestDate, photos[], remark, shipmentId` |
| POST | `/admin-api/farm/harvest/create` | `{ adoptionId, crop, weightJin, harvestDate, remark }`；`weightJin <= 0` → 400。同时累加认养的 `harvestedJin` |
| GET | `/admin-api/farm/shipment/page` | 字段 `id, no, harvestId, adoptionId, memberName, receiverName, receiverMobile, address, weightJin, carrier, logisticsNo, coldChain, status, tracks[{ time, content }], createTime` |
| POST | `/admin-api/farm/shipment/create` | `{ harvestId, carrier, logisticsNo }`；收成已发过货 → 400。状态直接到「运输中」，自动生成两条轨迹 |

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
| POST | `/farm/order/create` | 是 | `{ productId, careMode, contractAgreed, receiverName, receiverMobile, address }`。未勾选合同 → `1_030_000_001`；商品下架/该档未开放 → 400。返回 `{ id, no, amount }`，状态待支付 |
| POST | `/farm/order/pay` | 是 | `{ id }` 模拟支付：状态 → 认养中，绑定一个空闲资产，生成认养关系；按托管方式生成自管任务（全托管 0 项、半托管 2 项、不托管 5 项）和管家任务。返回 `{ orderId, adoptionId }`。错误 `1_030_000_002`、`1_030_000_003` |
| GET | `/farm/order/get?id=` | 是 | 订单 + 合同 |
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

## 5. 不在契约里的

- 分账、真实支付、电子合同签署、直播流、IoT 设备接入：页面用演示数据代替并标出「演示数据」。
- 退款售后、优惠券、积分、分销、社区。
