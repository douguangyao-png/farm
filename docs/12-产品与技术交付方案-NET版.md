# 12 · 认养农业 SaaS 产品与技术交付方案（.NET 版）

> **文档性质**：可直接交付研发团队的技术实施方案（整合 01-PRD、02-ER、10-托管模式设计）
> **技术栈**：.NET 10 LTS + ASP.NET Core + EF Core + PostgreSQL ｜ 前端 uni-app(Vue3) + Vue3 后台
> **目标**：12 周交付 MVP 并落地 1–2 个真实农场试点
> **配套文档**：`01-PRD.md`（需求）、`02-ER设计.md`+`02-ER.svg`（数据模型）、`03-竞品对比.md`、`05-定价模型.md`、`10-托管模式与农资商城设计.md`、`11-后端技术选型评估-NET可行性.md`（选型论证）、三端 HTML 演示原型（07/08/09）

---

## 0. 修订说明

本文取代 `04-技术选型与MVP排期.md` 中的技术栈结论（原文档倾向 Go/Java，现结论为 **.NET 10**，论证见 `11`）。`04` 中的 MVP 范围与节奏仍可参考，但排期以本文第十三节为准。

---

# 第一部分 · 产品需求（整合 PRD）

## 1. 产品概述

| 项 | 内容 |
|---|---|
| 产品定位 | 认养农业"操作系统"——面向经营者的**多租户数字化经营中台（B2B2C）**：经营者开通租户即用，服务其自有认养客户 |
| 目标客户 | 家庭农场、农民合作社、区域农业企业、文旅农庄、研学基地（全国 1.8 万+ 同类项目，以小微为主） |
| 商业模式 | SaaS 订阅（基础/专业/旗舰）+ 交易抽成 1–3% + 实施定制费 + 硬件/保险分润 |
| 三大差异化 | ① **合规内置**（对公收款/电子合同/分账/保险/风控词库）② **真透明**（IoT+直播+溯源+防摆拍校验）③ **托管三档 + 农资商城**（降门槛获客、高频复购、农场降本增毛利） |
| 成功标准 | 经营者 30 分钟自助开通并上架第一个认养商品；认养人 3 分钟内完成下单到签约 |

## 2. 角色与权限模型

| 角色 | 端 | 关键诉求 |
|---|---|---|
| 平台运营 | 平台管理端 | 租户审核/开通/停用、计费账单、风控合规监控、模板库（合同/套餐/话术）、全局数据 |
| 租户-经营者 | Web 后台 | 卖认养、管基地资产、管订单客户、看经营数据 |
| 子账号 | Web 后台 | 按角色授权（经营/管家/财务），数据范围受限 |
| 管家 | App / 企微 | 农事打卡、任务执行、客户跟进、发货上报、代管工单 |
| 认养用户（C 端） | 小程序/H5 | 认养、看生长、自管任务、买农资、收产出、到访、互动 |
| 分销团长 | 小程序 | 推广赚佣金（二期） |

**权限模型**：平台 > 租户管理员 > 子账号；RBAC（角色-权限-数据范围）。**所有业务表强制 `tenant_id` 隔离，双重校验（EF 全局过滤 + 应用层断言）**。

## 3. 功能模块总表

### 3.1 消费者小程序 / H5（原型 07，47 页）

| 模块 | 功能点 | MVP |
|---|---|---|
| 浏览发现 | 首页（金刚区/Banner/直播中基地/热门认养）、搜索、认养商城（分类）、基地列表/详情（资质/在售/动态）、收藏、平台公告 | P0 |
| 交易合约 | 商品详情（**托管三档选择**、规格/周期、库存进度、合规提示）、确认订单（地址/配送/券/积分）、**电子合同**、支付、支付结果、订单列表、退款售后、合约中心 | P0 |
| 认养管理 | 我的认养、认养详情（概览/**实时监控**/**生长档案**/**溯源**/收获配送）、IoT 环境看板、农事记录详情、质检报告、物流详情、认养转赠、发表评价 | P0 |
| **托管与农资** | **托管方式对比**（13 项 × 三档责任矩阵）、**农资与服务商城**（6 类目）、农资详情/结算、**我的农资**、**自管任务**（含委托代办/到访/加购） | P0（三档）/ P1（商城） |
| 到访服务 | 到访预约、在线管家（IM）、作物问诊（AI 识别）、任务中心 | P1 |
| 社区个人 | 农场日记列表/详情/发布、我的、会员中心、积分、券包、地址、邀请有礼、消息中心、设置 | P1 |

### 3.2 经营者后台（原型 08，34 页）

| 模块 | 功能点 | MVP |
|---|---|---|
| 经营看板 | 工作台（GMV 曲线、待办、风控预警、认养位使用率、今日农事） | P0 |
| 商品与资产 | 认养商品、**发布认养商品（含高风险文案拦截）**、**托管模式配置**、地块资产（网格+二维码）、畜禽资产（耳标）、IoT 设备、**农资与服务商品** | P0 |
| 订单与履约 | 认养订单、订单详情、合同管理、退款审核、**农资订单**、配送批次、收成录入、**代管服务工单** | P0 |
| 内容与监控 | 农事记录发布（机器校验）、视频监控、内容运营 | P0 |
| 客户与人力 | 客户管理、客户分层、管家管理 | P1 |
| 营销推广 | 优惠券、活动管理、推广合规设置 | P1 |
| 财务 | 交易流水、分账结算 | P0（分账 P1） |
| 数据/合规/系统 | 经营分析、资质证照、**风险自检（合规健康分）**、资金公示、套餐租户、角色权限、消息模板 | P1（合规自检 P0） |

### 3.3 管家 App（原型 09，17 页）

今日工作台 → 任务清单 → 任务详情 → **农事打卡**（现场拍照 + EXIF/GPS 校验 + 环境自动带入 + 收成明细）→ 提交后自动联动（进审核队列 / 写入 C 端档案 / 推送 / 计入批次+上链）；标的详情、客户聊天、作物问诊处理、配送扫码核销、异常上报、**农资代办**、**代管服务工单**、**自管认养人指导**、我的绩效。

### 3.4 平台管理端

租户审核开通停用、套餐计费账单、风控大屏（虚构基地/刷单/超范围/承诺收益预警）、全局数据、模板库（合同版本 v3.2/v2.1/v1.4、套餐模板、话术）。

## 4. 核心业务流程

**流程 1 · 认养下单**
```
浏览商品 → 选择托管方式(全/半/不) + 规格 + 周期
  → 系统按模式计算价格、匹配合同模板版本、生成《产出浮动告知书》
  → 确认订单（券/积分/地址）→ 勾选协议（强制，未勾选不可支付）
  → 电子合同签署（e签宝/法大大，CA 存证）
  → 微信支付（进平台商户号，对公，禁止个人码）
  → 支付回调（幂等）→ 生成 adoption 认养关系 + 绑定实体（地块/耳标）
  → 按 duty_matrix 初始化任务计划 → 推送管家接单
```

**流程 2 · 农事记录（透明化主线）**
```
任务触发（作物日历 / IoT 阈值 / 手工派发）
  → duty_matrix 判定 owner
     ├── farm     → 管家 App 任务池 → 打卡（照片+GPS+EXIF 时间+环境快照）
     └── customer → C 端「自管任务」→ 推送提醒
                        └─ 逾期 48h → 自动生成「委托管家代办」工单（含报价）
  → 打卡提交 → 机器校验（EXIF时间/GPS围栏/重复画面/设备指纹）
  → 经营者后台审核 → 写入生长档案 → C 端时间轴可见
  → 推送订阅消息 → 计入配送批次 → 溯源链追加 + 哈希上链
```

**流程 3 · 履约交付**
```
到期/产出 → 收成录入（称重留痕）→ 打包 → 批次发货（冷链轨迹）
  → C 端物流详情 → 签收确认 → 订单完成 → 触发评价/复购
```

**流程 4 · 分账结算**
```
用户支付 → 平台商户号（资金不进平台自有账户，仅技术服务费）
  → T+1 分账：经营者对公 + 平台抽成（1–3%）
  → 代管工单：管家 70% / 农场 20% / 平台 10%（未完成冻结，完成解冻）
  → 出具账单/发票
```

## 5. 托管模式与农资商城（业务规则，完整定义见 `10`）

### 5.1 三档定义

| 维度 | 全托管 | 半托管 | 不托管 |
|---|---|---|---|
| 认养费 | 基准价 ¥1,280 | 基准 −¥420 = ¥860 | 基准 −¥780 = **¥500** |
| 农场负责 | 全部农事+投入品+保险 | 整地/播种/统防/采收 + 12 次配送 | 土地/水电/工具借用/技术指导/监控 |
| 用户负责 | 无 | 浇水、除草、搭架、追肥（农资自购） | 全部农事（农资自购） |
| 每周 C 端待办 | 0 项 | ≈2 项 | ≈5 项 |
| 产出承诺 | 保底 240 斤，不足补足 | 按实际交付，不保底 | 风险自担 |
| 农业保险 | 含 | 统防部分含 | 建议自购 ¥96/年 |
| **合同模板** | 标准 v3.2 | 半托管 v2.1（含责任划分附件） | 不托管 v1.4（土地租赁+服务协议） |
| 农场全年 ARPU | ¥1,280 | **≈¥1,480** | ≈¥1,330 |
| 农场人工投入 | 100% | ≈55% | ≈20% |

**切换规则**：每周期可切换 1 次；低→高补差价，高→低按剩余周期折算退差额（扣已发生投入）；地块编号、摄像头绑定、历史档案连续保留。

### 5.2 农资商城

- **六类目**：种子 / 肥料 / 农具 / 生物防治 / 代管服务 / 保险
- **强制绑定地块**：`supply_order.land_id` 非空，否则无法下单
- **投入品留痕**：自购农资必须登记 `batch_no` + `used_at` + `dosage`；未登记则该地块档案标「待补充」，**第三方检测报告无法出具**
- **两来源合并**：`input_record.source = farm | customer`，在 C 端「溯源档案-投入品清单」统一展示

### 5.3 任务引擎（最值钱的自动化）

```
作物日历 + IoT 数据 → 生成农事任务 → duty_matrix 判定 owner
   ├─ farm     → 管家任务池
   └─ customer → C 端自管任务 → 逾期 48h → 推送「委托管家代办」
                                        → service_order → 分账 70/20/10
```
自管任务逾期不是服务失败，是**商机**（试点数据：上月转化 68 单、¥6,240 纯增量）。

## 6. 合规底线（系统强制，非配置项）

| # | 规则 | 系统实现 |
|---|---|---|
| C1 | 禁止任何"固定现金收益/返利/回购"表述 | 商品发布、营销文案、合同模板统一走**敏感词+语义规则引擎**，命中即拦截并给出改写建议 |
| C2 | 收款必须走对公/平台商户号 | 系统层面**不提供个人收款码配置项**；资金进第三方监管账户 |
| C3 | 电子合同必须签署 | 下单流程强制勾选 + 签署成功才允许支付；合同模板按托管模式分版本 |
| C4 | **化学农药禁止对个人零售** | `supply_spu.retail_allowed = false`，只能作为"农场代施服务"绑定工单出售 |
| C5 | 自购农资必须留痕 | 未登记批次+施用时间 → 地块档案标「待补充」→ 无法出具检测报告 |
| C6 | 半托管/不托管产出责任 | 按模式展示不同《产出浮动告知书》《免责条款》，勾选后方可支付 |
| C7 | 退款规则 | 未启动种养可退；种养中按已发生成本比例退（退款审核页自动试算） |
| C8 | 农业保险 | 建议对接，覆盖疫病/自然灾害；不托管模式强制提示自购 |
| C9 | 平台不触碰资金 | 仅收技术服务费；代管工单走分账冻结/解冻 |
| C10 | 数据合规 | HTTPS、敏感信息脱敏、操作审计日志、个人信息授权同意 |

---

# 第二部分 · 技术方案（.NET）

## 7. 系统架构

### 7.1 总体架构（模块化单体）

```
┌─────────────────────────────────────────────────────────────┐
│ 展现层                                                       │
│  C端 uni-app(微信小程序/H5) │ 后台 Vue3 │ 管家 uni-app │ 平台端 │
└─────────────────────────────────────────────────────────────┘
                    ↓ HTTPS / WSS(SignalR)
┌─────────────────────────────────────────────────────────────┐
│ 网关/边缘：Nginx（反代、TLS、限流、静态/CDN 回源）             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 应用层（ASP.NET Core 10 · 单进程，模块化单体）                │
│  ┌──────────┬──────────┬──────────┬──────────┬────────────┐ │
│  │ 商品/资产 │ 交易/合约 │ 履约/配送 │ 内容/溯源 │ 客户/营销  │ │
│  │ 域       │ 域       │ 域       │ 域       │ 域         │ │
│  └──────────┴──────────┴──────────┴──────────┴────────────┘ │
│  ┌──────────┬──────────┬──────────┬──────────┬────────────┐ │
│  │ 托管/任务 │ 农资商城 │ 财务/分账 │ IoT 接入  │ 合规风控   │ │
│  │ 引擎域   │ 域       │ 域       │ 域       │ 域         │ │
│  └──────────┴──────────┴──────────┴──────────┴────────────┘ │
│ 共享内核：多租户 / 鉴权授权 / 审计 / 领域事件 / 异常处理 / 幂等 │
└─────────────────────────────────────────────────────────────┘
        ↓            ↓          ↓          ↓          ↓
┌──────────┐ ┌───────────┐ ┌─────────┐ ┌────────┐ ┌──────────┐
│PostgreSQL│ │TimescaleDB│ │ Redis   │ │RabbitMQ│ │ 对象存储  │
│ 业务主库 │ │ 传感器时序 │ │ 缓存/锁 │ │ CAP事件│ │ OSS/COS  │
└──────────┘ └───────────┘ └─────────┘ └────────┘ └──────────┘
外部：微信支付(服务商分账) · e签宝/法大大 · 微信订阅消息 · 短信
      EMQX(MQTT) · 直播(SRS/WebRTC) · 快递鸟 · 农险机构
```

### 7.2 分层与依赖方向

```
SharedKernel（多租户、值对象、领域事件接口、异常、工具）
        ↑                ↑                  ↑
    Domain（实体、聚合、领域服务、领域事件） ←── 依赖倒置
        ↑
  Application（应用服务、DTO、校验、用例编排、权限检查）
        ↑
Infrastructure（EF Core、Redis、MQ、支付/合同/短信适配、文件存储）
        ↑
   Host / Web（Controllers、SignalR Hub、Hangfire、中间件、DI 装配）
```

### 7.3 多租户实现

- **存储策略**：共享库 + `tenant_id` 行级隔离（默认）；旗舰客户可独立库（ABP 支持按租户配置连接串 / 自建 `ITenantConnectionStringResolver`）
- **租户解析**：`TenantResolveContributor` 按序尝试 → 域名/子域名 → Header(`X-Tenant-Id`) → JWT Claim → 用户绑定
- **数据隔离**：`IMultiTenant` 接口 + EF Core **全局查询过滤器**（`HasQueryFilter`）；所有写操作自动注入 `TenantId`
- **二次防线**：Repository 层与 AppService 层断言 `TenantId == CurrentTenant.Id`，异常即 `TenantMismatchException`（防过滤器被 `IgnoreQueryFilters` 绕过）
- **租户配额**：套餐限制（基地数/资产数/子账号数/存储配额），在应用服务入口统一校验

## 8. 技术栈清单

| 层 | 选型 | 备注 |
|---|---|---|
| 运行时 | **.NET 10 LTS** | 支持至 2028-11；不要用 .NET 8（2026-11-10 EOL） |
| 应用框架 | ASP.NET Core + **ABP Framework（轻量用法）** 或 **精简自建** | 决策见 `11` 第七节；默认推荐精简自建 |
| ORM | **EF Core 10 + Npgsql** | Code First + Migration |
| 业务库 | **PostgreSQL 16+** | JSONB 存 `duty_matrix` / `care_modes` / `specs` |
| 时序 | **TimescaleDB**（PG 扩展） | 备选 TDengine；选扩展可少维护一个组件 |
| 缓存 | Redis 7 | `IDistributedCache`、分布式锁、幂等键、限流 |
| 事件 | **CAP**（本地消息表 + RabbitMQ） | Outbox 保证最终一致 |
| 实时 | **SignalR** | IoT 告警 / IM / 记录通知 |
| 调度 | **Hangfire** | 任务引擎、逾期扫描、到期提醒 |
| IoT | **MQTTnet** + `IHostedService` | 订阅 EMQX，写 TimescaleDB |
| 对象映射 | **Mapster** | 规避 AutoMapper 商业授权 |
| 校验 | FluentValidation（社区版）/ DataAnnotations + 自研规则引擎 | 注意商业授权条款 |
| 韧性 | **Polly** | 支付/合同/短信调用的重试与熔断 |
| 日志 | Serilog（结构化）→ Seq / ELK | 带 TenantId / UserId / TraceId |
| 可观测 | OpenTelemetry + Prometheus + Grafana + HealthChecks | |
| 测试 | xUnit + **Testcontainers** + Shouldly + NBomber | 集成测试用真实 PG/Redis 容器 |
| 前端 C 端/管家 | uni-app（Vue3 + TS + Vite） | 小程序 + H5，一套代码 |
| 前端后台 | Vue3 + TS + Element Plus（Vite） | 经营者后台 + 平台端 |
| 部署 | Docker + compose + Nginx（Y1 不上 K8s） | 镜像发布 + 滚动重启 |
| CI/CD | Gitee CI / GitHub Actions / 云效 | build→test→publish→镜像→部署 |

## 9. 数据模型（EF Core 落地）

> 完整字段见 `02-ER设计.md` 与 `02-ER.svg`；本节给出 .NET 落地约定与新增/调整。

### 9.1 通用约定

| 约定 | 说明 |
|---|---|
| 主键 | `long`（雪花 ID，用 `IdGenerator`），避免自增暴露业务量 |
| 租户 | 所有业务表实现 `IMultiTenant`，复合索引以 `tenant_id` 打头 |
| 审计 | `CreationTime / CreatorId / LastModificationTime / LastModifierId / IsDeleted`（软删除） |
| 金额 | `decimal(18,2)`，**禁止 float/double** |
| 枚举 | 落库为 `smallint` 或 `varchar`，EF 用 `HasConversion<string>()`（可读性优先） |
| 软删除 | 统一全局过滤 `IsDeleted == false` |
| 并发 | 关键表（商品库存、资产状态）加 `byte[] RowVersion`（乐观锁） |
| 时间 | `DateTimeOffset` 或统一 UTC `DateTime`，展示层转本地 |

### 9.2 核心实体清单（16 张基础表 + 6 张托管/农资表）

**基础域**

| 实体 | 关键字段 |
|---|---|
| `Tenant` | Id, Name, Plan, ExpireAt, Status, Quota(JSON) |
| `FarmBase` | TenantId, Name, Address, Geo(Point), CertNo, CertFiles(JSON), Status |
| `Asset`（地块/畜禽/果树统一表） | TenantId, BaseId, AssetType(plot/animal/tree), Code(二维码/耳标), Specs(JSON), IotDeviceId, Status |
| `Product`（认养商品） | TenantId, BaseId, AssetType, Title, BasePrice, CycleDays, YieldRule, Stock, Sold, **CareModes(JSON)**, **DefaultCareMode** |
| `Member` | TenantId, Openid, Unionid, Phone, Level, SourceChannel |
| `Order` | TenantId, MemberId, ProductId, **CareMode**, Amount, PayStatus, ContractId, RefundStatus |
| `Adoption`（认养关系） | OrderId, TenantId, MemberId, AssetId, AssetType, CareMode, StartAt, EndAt, Status, OutputProgress(JSON) |
| `Steward`（管家） | TenantId, BaseId, Name, Phone, KeeperRatio |
| `GrowthRecord` | TenantId, AdoptionId, AssetId, StewardId, Type, Content, Media(JSON), Geo, IotSnapshot(JSON), Hash, PrevHash |
| `IotDevice` | AssetId, DeviceType(cam/sensor/gps), Status, LastHeartbeat |
| `SensorData`（时序） | DeviceId, Metric, Value, Ts |
| `Shipment` | OrderId, AdoptionId, LogisticsNo, Carrier, ColdChain(JSON), SignedAt |
| `Payment` | OrderId, Amount, Channel, TradeNo, PaidAt, IdempotencyKey |
| `Settlement`（分账） | OrderId/ServiceOrderId, MerchantAmount, KeeperAmount, PlatformFee, Status, SettledAt |
| `Contract` | OrderId, TemplateId, Version, SignedAt, FileUrl, CaSerialNo |
| `Distributor` | TenantId, MemberId, ParentMemberId, OrderId, Commission |

**托管与农资域（见 `10`）**

| 实体 | 关键字段 |
|---|---|
| `CareModeConfig` | SkuId, Mode(full/semi/self), PriceDelta, **DutyMatrix(JSON)**, OutputPromise, ContractTplId, SwitchRule(JSON), SupplyPackage(JSON) |
| `SupplySpu` | TenantId, Cat(seed/fert/tool/bio/svc/ins), Price, Cost, Stock, FitModes(JSON), BatchNo, **RetailAllowed**, **NeedWorkorder**, BindLandType |
| `SupplyOrder` | AdoptionId, **LandId(必填)**, SkuId, Qty, Amount, DeliverWay, **TraceState**(待登记/已写入) |
| `InputRecord` | LandId, Source(farm/customer), Name, BatchNo, UsedAt, Dosage, Operator |
| `TaskTemplate` | CropId, Stage, Trigger(crop_calendar/iot_threshold/manual), TriggerRule(JSON), Owner(keeper/customer), OverdueAction(JSON) |
| `ServiceOrder`（代管工单） | FromTaskId, LandId, CustomerId, KeeperId, ServiceType, Price, SplitRule(JSON), ProofPhotos(JSON), Status |

### 9.3 关键关系

```
Tenant 1─N FarmBase 1─N Asset
FarmBase 1─N Product 1─N CareModeConfig(×3)
Product 1─N Order 1─1 Adoption N─1 Asset
Adoption 1─N GrowthRecord ；Adoption 1─N SupplyOrder
Asset  1─N InputRecord （source = farm | customer，合并进溯源）
CareModeConfig.DutyMatrix ──决定──> TaskTemplate.Owner
TaskTemplate ──生成──> Task(keeper 池 / customer 自管) ──逾期──> ServiceOrder
Order 1─1 Contract ；Order 1─N Payment ；Order/ServiceOrder 1─1 Settlement
```

### 9.4 索引要点

- 所有业务表：`(tenant_id, ...)` 复合索引打头
- `order(tenant_id, product_id, pay_status)`、`adoption(tenant_id, asset_id)`、`adoption(tenant_id, member_id, status)`
- `growth_record(tenant_id, adoption_id, creation_time DESC)`（最大表，按年分区）
- `input_record(tenant_id, land_id, used_at)`
- `sensor_data(device_id, ts DESC)`（TimescaleDB hypertable，30 天压缩策略）
- 唯一索引：`asset(tenant_id, code)`、`payment(trade_no)`、`payment(idempotency_key)`

## 10. API 规范

| 项 | 约定 |
|---|---|
| 风格 | RESTful（资源 + 动词）+ 少量 RPC 式动作端点（如 `/orders/{id}/refund`） |
| 版本 | URL 前缀 `/api/v1/` |
| 认证 | `Authorization: Bearer <JWT>`；C 端 JWT（openid）、后台 JWT（账号+角色）、管家端同后台 |
| 租户 | 后台/管家可省略（从 token 取）；平台端显式 `X-Tenant-Id` |
| 响应 | 统一包装 `{ code, message, data, traceId }`；HTTP 200 + `code=0` 为成功；业务失败用 `code=40001` 类错误码 |
| 分页 | `?page=1&pageSize=20`，返回 `{ items, total, page, pageSize }`，`pageSize` 上限 100 |
| 幂等 | 写操作支持 `Idempotency-Key` 头（支付回调、下单、发货）；Redis 存 24h |
| 限流 | 按租户 + IP 双维度（`AspNetCoreRateLimit` 或 Nginx 层）；C 端下单 5 次/分钟 |
| 文档 | Swagger/OpenAPI 自动生成（NSwag 或 Swashbuckle），与前端契约同步 |
| 实时 | SignalR Hub：`/hubs/farm`（IoT 实时）、`/hubs/im`（聊天）、`/hubs/notify`（通知） |
| BFF | 小程序端高频聚合接口（首页、认养详情）在应用层做 `AppService` 聚合，避免 N 次请求 |

**核心接口分组（示例）**

| 分组 | 端点示例 |
|---|---|
| 商品 | `GET /api/v1/products`、`GET /api/v1/products/{id}`（含 careModes 三档）、`POST /api/v1/products`（含合规校验） |
| 托管 | `GET /api/v1/products/{id}/care-modes`、`GET /api/v1/care-modes/compare`、`PUT /api/v1/products/{id}/care-modes` |
| 交易 | `POST /api/v1/orders`、`POST /api/v1/orders/{id}/contract/sign`、`POST /api/v1/payments/wechat/notify`、`POST /api/v1/orders/{id}/refund` |
| 认养 | `GET /api/v1/adoptions`、`GET /api/v1/adoptions/{id}`、`GET /api/v1/adoptions/{id}/archive`、`/trace`、`/iot` |
| 自管任务 | `GET /api/v1/adoptions/{id}/tasks`、`POST /api/v1/tasks/{id}/complete`、`POST /api/v1/tasks/{id}/entrust`（转委托工单） |
| 农资 | `GET /api/v1/supplies`、`POST /api/v1/supply-orders`（强制 landId）、`POST /api/v1/input-records`（批次登记） |
| 管家 | `POST /api/v1/steward/punch`、`GET /api/v1/steward/tasks`、`POST /api/v1/service-orders/{id}/accept` |
| 平台 | `GET /api/v1/platform/tenants`、`/risk-alerts`、`/settlements` |

## 11. 关键引擎实现

### 11.1 任务引擎（Hangfire）

- **任务生成**：`CropCalendarJob`（每日 06:00 扫描作物日历，按 `TaskTemplate.Trigger=crop_calendar` 生成任务）+ `IotThresholdJob`（每 5 分钟扫描传感器，命中 `trigger_rule` 生成任务，如 `soil_moisture < 22% AND rain_48h = 0 → 浇水`）
- **归属判定**：读取 `CareModeConfig.DutyMatrix[环节]` → `farm` 进管家任务池；`customer` 进 C 端自管任务并推送订阅消息
- **逾期转商机**：`OverdueScanJob`（每 30 分钟）：
  ```
  自管任务 due_at < now-48h 且未完成
    → 标记 overdue
    → 按 overdue_action 生成「委托管家代办」营销位（报价取自 ServicePrice 表）
    → 用户确认 → 创建 ServiceOrder（status=待接单，资金冻结）
    → 推给地块所属管家 → 接单→执行打卡→完成→解冻分账 70/20/10
    → 自动写入 GrowthRecord
  ```
- **可靠性**：Hangfire 持久化到 PG；Job 幂等（按 `task_id` 去重）；失败自动重试 3 次后进死信并告警

### 11.2 分账引擎

- 支付完成 → `PaymentSucceeded` 领域事件（CAP 发布，本地消息表保证不丢）
- `SettlementAppService` 消费事件 → 按规则计算（认养单：经营者 + 平台 1–3%；代管工单：管家 70 / 农场 20 / 平台 10）
- 调用**微信支付服务商分账**：未完成先**冻结**，完成后**解冻并分账**；失败进重试队列 + 人工工单
- 所有分账流水 `Settlement` 落库，支持对账导出

### 11.3 溯源与存证

- 每条 `GrowthRecord` / `InputRecord` 计算 `Hash = SHA256(prevHash + 规范化的业务字段)`，形成**哈希链**（MVP 用哈希链，二期接区块链做定期锚定：每 10 分钟把链头哈希写入存证服务）
- C 端「溯源档案」按 种植→管护→投入品→采收→质检→配送 六段展示，投入品区分 farm/customer 两来源
- 检测报告：对接 CNAS 实验室数据或手工录入 `report` 表

### 11.4 合规风控引擎

```
ComplianceCheckPipeline（MediatR Behavior 或自建 Command Dispatcher 拦截）
  ① 敏感词库：保底/稳赚/年化/收益/回购/返利/投资/分红/拉新提成 …
  ② 语义规则：价格 + "承诺" 同时出现、产出量与周期不匹配
  ③ 类目规则：supply_spu.cat=pesticide → 强制 retail_allowed=false + need_workorder=true
  ④ 资金规则：任何"个人收款码/私户"配置项不存在（配置层面封堵）
  命中 → 阻断提交 + 返回改写建议 + 记入 risk_alert
```
- 后台「风险自检」页：租户级合规健康分（资质证照完整度、资金路径、合同签署率、文案命中数、溯源完整度），可导出《合规经营报告》

### 11.5 防"摆拍式透明"（管家打卡校验）

| 校验 | 实现 |
|---|---|
| EXIF 时间 | 照片拍摄时间在任务窗口内（±30 分钟） |
| GPS 围栏 | 坐标在基地 GeoFence 内（PostGIS 或 Haversine 计算） |
| 重复画面 | 感知哈希（pHash）比对近 30 天记录，相似度 > 0.95 判定复用 |
| 设备指纹 | 管家账号绑定设备，异常设备需二次审核 |
| 环境一致性 | 打卡时自动带入该地块 IoT 快照（温湿度）作为佐证 |

### 11.6 IoT 接入

```
MqttWorker : BackgroundService
  → MQTTnet 连接 EMQX（TLS + 客户端证书）
  → 订阅 tenant/{id}/device/+/telemetry
  → 解析 → 批量写 TimescaleDB（每 5s 或 500 条 flush）
  → 阈值命中 → 发布 IotThresholdExceeded 领域事件 → 任务引擎
  → SignalR 推送到观看该地块的 C 端连接（Group = asset:{id}）
```

## 12. 非功能需求与 SLA

| 项 | 指标 |
|---|---|
| 响应 | 列表/详情 P95 < 300ms；复杂聚合 < 800ms；写入 < 500ms |
| 吞吐 | 目标峰值 800 QPS（3 × 4C8G 实例，余量 50%）；大促 3000 QPS 靠读缓存 + 临时扩容 + 写限流 |
| 可用性 | 99.9%（月度停机 < 43 分钟）；支付/分账链路 99.95% |
| 数据 | PG 每日全备 + WAL 归档（PITR，RPO < 5 分钟）；对象存储跨区域复制；每季度恢复演练 |
| 安全 | 全站 HTTPS(HSTS)；JWT 短期(2h)+Refresh(7d)；密码/密钥入 KMS 或环境变量，禁止入库；SQL 参数化（EF 默认）；上传文件类型白名单 + 病毒扫描；越权测试纳入 CI |
| 合规 | 个人信息授权同意；敏感数据（手机号/身份证）加密存储 + 展示脱敏；操作审计留痕 ≥3 年；等保二级（按需） |
| 容量 | 单租户上限：基地 20 / 资产 5000 / 子账号 20 / 存储 200GB（按套餐配额，可配置） |
| 监控告警 | 接口 P95、错误率、Hangfire 失败数、支付回调积压、MQTT 断连、磁盘/连接数；告警进企微 |

## 13. 交付排期与团队

### 13.1 12 周 MVP 排期

| 周次 | 阶段 | 交付物 |
|---|---|---|
| **W1** | 需求冻结 | PRD/原型定稿、**第三方 SDK 可用性核实（附录 C）**、接口契约 v1、UI 设计稿 |
| **W2** | 数据建模 | ER 定稿、EF Core 实体 + Migration、种子数据、索引方案 |
| **W2–3** | 基建 | 解决方案骨架、**多租户**、JWT 权限、**审计**、统一响应/异常/日志、CI/CD、Swagger |
| **W3–5** | 商品与资产域 | 租户/基地/资产台账（二维码、耳标）、认养商品、**托管三档配置 + 责任矩阵 + 合同分版本** |
| **W5–7** | 交易域 | 下单、库存与乐观锁、电子合同对接、微信支付（含回调幂等）、订单管理、退款试算 |
| **W6–9** | C 端 | 首页/商城/详情（三档切换）/下单/合同/支付/我的认养/认养详情 5 Tab |
| **W7–9** | 管家端 + 内容 | 任务池、**农事打卡**（含 4 项防摆拍校验）、记录审核、写入生长档案、推送 |
| **W8–10** | 履约与溯源 | 收成录入、配送批次、物流轨迹、C 端收获配送、哈希链存证 |
| **W10–11** | **农资商城（P1）** | SPU/批次/绑定地块/投入品台账合并；C 端商城与我的农资 |
| **W11** | 合规中心 | 敏感词拦截、合规健康分、资质证照、资金公示 |
| **W11–12** | 试点上线 | 压测（NBomber）、安全自查、1–2 个农场灰度、50–100 认养人闭环、修复 |

**关键路径**：多租户(W3) → 商品+托管三档(W5) → 支付分账(W7) → C 端主流程(W9) → 试点(W12)

### 13.2 二期（W13–16）

任务引擎自动化（作物日历 + IoT 阈值 + 逾期转委托）→ 代管工单 + 分账 70/20/10 → IoT 设备接入与实时看板 → 直播 → 营销（券/拼团/分销）→ 农险对接

### 13.3 8 周极简版（若必须抢时间）

保留：租户+权限、商品+**托管三档**、下单+合同+支付、我的认养+生长档案（管家打卡）、订单管理、退款。
砍掉：农资商城、IoT 实时、营销、会员积分、社区、数据洞察、代管工单、物流轨迹对接（改手工录入单号）。
代价：缺少"透明化"深度与农资复购，试点说服力下降约 30%，但可提前 4 周接触真实客户。

### 13.4 团队配置

| 角色 | 人数 | 职责 |
|---|---|---|
| 后端（.NET） | 2 | 1 资深（架构/领域模型/支付分账）+ 1 中级（CRUD/接口/对接） |
| 前端 | 2 | 1 小程序/管家端（uni-app）+ 1 后台（Vue3） |
| 产品/设计 | 1 | 需求、合规规则、UI（可复用现有 07/08/09 原型） |
| 测试/运维 | 1（可兼职） | 用例、自动化冒烟、部署监控 |
| 农业顾问 | 兼职 | SOP、合规话术、基地对接 |

合计 **5–6 人**。

### 13.5 里程碑与验收

| 里程碑 | 时点 | 验收标准 |
|---|---|---|
| M1 架构可跑 | W3 末 | 多租户隔离通过单测；登录/权限/审计可用；CI 绿灯 |
| M2 商品可卖 | W5 末 | 经营者 30 分钟自助建基地→建地块→上架三档商品 |
| M3 交易闭环 | W7 末 | 沙箱环境完成 下单→签约→支付→生成认养关系 |
| M4 C 端可用 | W9 末 | 小程序完整走通首页→详情→下单→我的认养→生长档案 |
| M5 试点上线 | W12 末 | 1–2 农场真实运行，≥50 认养人，≥200 条农事记录，0 越权/0 资金差错 |

## 14. 质量、CI/CD 与运维

| 环节 | 方案 |
|---|---|
| 单测 | xUnit；领域层（价格计算、责任矩阵判定、分账比例、哈希链）覆盖率 ≥70% |
| 集成测试 | **Testcontainers** 起真实 PG/Redis；关键链路（下单-支付回调-生成认养）端到端 |
| API 测试 | Swagger + Postman/HTTP 文件集合纳入 CI 冒烟 |
| 压测 | NBomber：下单、认养详情、打卡提交三个场景；目标 P95 < 300ms @ 800 QPS |
| 越权测试 | 自动化用例：A 租户 token 访问 B 租户资源必须 403 |
| CI | push → restore/build/test（含集成测试）→ 生成 Migration SQL 检查 → 镜像 |
| CD | 手动确认后滚动更新；数据库 Migration 走独立审批步骤（禁止应用启动时自动 Migrate） |
| 灰度 | 先 1 个试点租户全量，观察 1 周后放量 |
| 回滚 | 镜像版本化 + Migration 回滚脚本（每个迁移必须写 Down） |
| 备份 | PG 每日全备 + WAL 归档；对象存储版本控制；月度恢复演练 |

## 15. 风险清单

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| T1 | 微信支付服务商分账资质审批慢（2–6 周） | 阻塞资金闭环 | **W1 立即启动申请**，审批期间用"普通支付 + 手工结算"兜底 |
| T2 | 电子合同对接与模板法务审核耗时 | 阻塞合规卖点 | 提前找法务过三版合同模板（v3.2/v2.1/v1.4）；对接前先用平台自存证 |
| T3 | 需求蔓延（营销/直播/IoT 都想做） | 延期 | 严格执行 13.1 范围；新增需求进二期池，每周评审一次 |
| T4 | 私有化/信创客户要求国产数据库 | 额外 1–2 周 POC | 合同明确默认 PG/MySQL；国产库单独报价 |
| T5 | 第三方 SDK 无 .NET 版 | 对接延期 | 附录 C 已列兜底（REST 自封，5 人日内）；W1 核实 |
| T6 | 小程序审核（涉农、涉支付类目） | 上线延期 | 提前准备《食品经营/农产品自销》类目材料；预留 2 周审核缓冲 |
| T7 | 合规文案误伤正常描述 | 体验下降 | 词库分级：硬拦截（保底/收益/返利）+ 软提示（高产/优质）；误报可申诉并记录 |
| T8 | 团队无 ABP 经验 | 起步慢 2 周 | 走路线 B 精简自建（默认推荐） |

---

# 附录

## 附录 A：解决方案目录结构

```
shared-farm.sln
├─ src/
│  ├─ Farm.SaaS.SharedKernel/          # 多租户、值对象、领域事件、异常、工具
│  ├─ Farm.SaaS.Domain/                # 实体、聚合、领域服务
│  │   ├─ Catalog/        (Product, CareModeConfig, Asset, FarmBase)
│  │   ├─ Trade/          (Order, Contract, Payment, Settlement)
│  │   ├─ Adoption/       (Adoption, GrowthRecord, InputRecord)
│  │   ├─ Steward/        (Steward, Task, ServiceOrder, PunchRecord)
│  │   ├─ Supply/         (SupplySpu, SupplyOrder)
│  │   └─ Compliance/     (RiskAlert, ComplianceRule)
│  ├─ Farm.SaaS.Application/           # 应用服务、DTO、校验、用例编排
│  │   ├─ Catalog/  Trade/  Adoption/  Steward/  Supply/  Compliance/
│  │   └─ Contracts/  (接口定义 + DTO)
│  ├─ Farm.SaaS.Infrastructure/        # EF Core、Redis、CAP、MQTT、支付/合同适配
│  │   ├─ EntityFrameworkCore/  (DbContext, Configurations, Migrations, Repositories)
│  │   ├─ Cache/  Events/  IoT/  Payments/  EContract/  Storage/  Sms/
│  └─ Farm.SaaS.Web/                   # Host
│      ├─ Controllers/  Hubs/  Jobs/  Middlewares/  Program.cs
├─ test/
│  ├─ Farm.SaaS.Domain.Tests/
│  ├─ Farm.SaaS.Application.Tests/      # Testcontainers 集成测试
│  └─ Farm.SaaS.LoadTests/              # NBomber
└─ deploy/
   ├─ docker-compose.yml  nginx/  grafana/  prometheus/
```

## 附录 B：关键代码骨架

### B1 · 多租户基类与全局过滤

```csharp
// SharedKernel
public interface IMultiTenant { long? TenantId { get; } }
public interface ISoftDelete  { bool IsDeleted { get; } }

public abstract class FullAuditedEntity<TKey> : IMultiTenant, ISoftDelete
{
    public TKey Id { get; protected set; } = default!;
    public long? TenantId { get; protected set; }
    public bool  IsDeleted { get; protected set; }
    public DateTime CreationTime { get; protected set; }
    public long? CreatorId { get; protected set; }
    public DateTime? LastModificationTime { get; protected set; }
    public long? LastModifierId { get; protected set; }
    public byte[]? RowVersion { get; protected set; }   // 乐观锁
}

// Infrastructure/EntityFrameworkCore/SaaSDbContext.cs
public sealed class SaaSDbContext : DbContext
{
    private readonly ICurrentTenant _currentTenant;
    public SaaSDbContext(DbContextOptions<SaaSDbContext> opt, ICurrentTenant currentTenant)
        : base(opt) => _currentTenant = currentTenant;

    protected override void OnModelCreating(ModelBuilder b)
    {
        foreach (var entity in b.Model.GetEntityTypes())
        {
            if (typeof(IMultiTenant).IsAssignableFrom(entity.ClrType))
            {
                // 租户过滤 + 软删除，双过滤器
                var p = Expression.Parameter(entity.ClrType, "e");
                var tenantId = Expression.Property(p, nameof(IMultiTenant.TenantId));
                Expression? body = Expression.Equal(
                    tenantId,
                    Expression.Convert(Expression.Constant(_currentTenant.Id), typeof(long?)));

                if (typeof(ISoftDelete).IsAssignableFrom(entity.ClrType))
                    body = Expression.AndAlso(body, Expression.Equal(
                        Expression.Property(p, nameof(ISoftDelete.IsDeleted)),
                        Expression.Constant(false)));

                entity.SetQueryFilter(Expression.Lambda(body, p));
                entity.AddIndex(tenantId).SetDatabaseName($"IX_{entity.ClrType.Name}_TenantId");
            }
        }
        b.ApplyConfigurationsFromAssembly(typeof(SaaSDbContext).Assembly);
    }

    // 写入时自动注入租户，防止漏填导致数据"裸奔"
    public override Task<int> SaveChangesAsync(CancellationToken ct = default)
    {
        foreach (var e in ChangeTracker.Entries<IMultiTenant>())
        {
            if (e.State == EntityState.Added && e.Entity.TenantId is null)
                e.Entity.GetType().GetProperty("TenantId")!
                    .SetValue(e.Entity, _currentTenant.Id);
            if (e.State == EntityState.Modified && e.Entity.TenantId != _currentTenant.Id)
                throw new TenantMismatchException("跨租户写入被拒绝");
        }
        return base.SaveChangesAsync(ct);
    }
}
```

### B2 · 托管模式与责任矩阵（领域层）

```csharp
public enum CareMode { Full, Semi, Self }

public sealed class CareModeConfig : FullAuditedEntity<long>
{
    public long ProductId { get; private set; }
    public CareMode Mode { get; private set; }
    public int PriceDelta { get; private set; }          // 相对基准价，负数=减
    public DutyMatrix DutyMatrix { get; private set; } = DutyMatrix.Empty;
    public string OutputPromise { get; private set; } = string.Empty;
    public string ContractTemplateVersion { get; private set; } = "v3.2";
    public IReadOnlyList<long> SupplyPackage { get; private set; } = Array.Empty<long>();

    public decimal EffectivePrice(decimal basePrice) => basePrice + PriceDelta;
    public DutyOwner OwnerOf(FarmWorkStep step) => DutyMatrix.OwnerOf(step);
}

// 13 个农事环节的执行方矩阵；以 JSONB 存储
public sealed class DutyMatrix
{
    private readonly Dictionary<FarmWorkStep, DutyOwner> _map;
    public static DutyMatrix Empty => new(new Dictionary<FarmWorkStep, DutyOwner>());
    private DutyMatrix(Dictionary<FarmWorkStep, DutyOwner> map) => _map = map;
    public DutyOwner OwnerOf(FarmWorkStep step)
        => _map.TryGetValue(step, out var o) ? o : DutyOwner.Farm;   // 缺省归农场，安全侧
    public static DutyMatrix Parse(string json) => /* System.Text.Json 反序列化 */ Empty;
}

public enum FarmWorkStep { 整地, 播种, 浇水, 施肥, 除草, 搭架, 整枝, 授粉, 生物防治, 统防统治, 采收, 分拣包装, 配送 }
public enum DutyOwner { Farm, Customer }
```

### B3 · 任务引擎：逾期转"委托代办"（Hangfire Job）

```csharp
public sealed class OverdueSelfTaskJob(IUnitOfWork uow, IRepository<SelfTask, long> tasks,
    IRepository<ServiceOrder, long> orders, IEventBus bus, ILogger<OverdueSelfTaskJob> log)
{
    public const int OverdueHours = 48;

    [AutomaticRetry(Attempts = 3)]
    public async Task RunAsync()
    {
        var deadline = DateTime.UtcNow.AddHours(-OverdueHours);
        var overdue = await tasks.Where(t =>
            t.Owner == DutyOwner.Customer &&
            t.Status == TaskStatus.Pending &&
            t.DueAt < deadline &&
            !t.Entrusted).ToListAsync();

        foreach (var t in overdue)
        {
            using var _ = log.BeginScope(new Dictionary<string, object?> { ["taskId"] = t.Id });
            t.MarkOverdue();

            // 逾期不是服务失败，是商机：推送"委托管家代办"
            var price = await PriceBook.QuoteAsync(t.Step, t.AssetId);
            t.OfferEntrust(price);

            await bus.PublishAsync(new SelfTaskOverdueEvent(
                t.TenantId!.Value, t.AdoptionId, t.Id, t.Step, price));
            log.LogInformation("自管任务 {TaskId} 逾期 {H}h，已推送委托代办报价 {Price}",
                t.Id, OverdueHours, price);
        }
        await uow.SaveChangesAsync();
    }
}

// 注册：RecurringJob.AddOrUpdate<OverdueSelfTaskJob>("overdue-scan", j => j.RunAsync(), "*/30 * * * *");
```

### B4 · 分账领域服务

```csharp
public sealed class SettlementCalculator
{
    // 认养单：经营者 + 平台技术服务费
    public Settlement SplitAdoption(decimal amount, decimal platformRate)
    {
        var fee = Math.Round(amount * platformRate, 2, MidpointRounding.ToEven);
        return Settlement.Create(amount - fee, keeperAmount: 0, fee);
    }

    // 代管工单：管家 70% / 农场 20% / 平台 10%
    public Settlement SplitServiceOrder(decimal amount)
    {
        var keeper   = Math.Round(amount * 0.70m, 2, MidpointRounding.ToEven);
        var farm     = Math.Round(amount * 0.20m, 2, MidpointRounding.ToEven);
        var platform = amount - keeper - farm;      // 尾差归平台，保证分账恒等
        return Settlement.Create(farm, keeper, platform);
    }
}

// 消费领域事件（CAP，本地消息表保证不丢）
public sealed class PaymentSucceededHandler : IEventHandler<PaymentSucceededEvent>
{
    public async Task HandleAsync(PaymentSucceededEvent e)
    {
        var s = _calc.SplitAdoption(e.Amount, e.PlatformRate);
        await _repo.InsertAsync(s);
        await _wxPay.FreezeAsync(e.TradeNo, s);          // 先冻结
        await _cap.PublishAsync(new SettlementFrozenEvent(s.Id));
    }
}
```

### B5 · 合规拦截（命令管道）

```csharp
public sealed class ComplianceBehavior<TReq, TRes> : ICommandPipeline<TReq, TRes>
{
    public async Task<TRes> HandleAsync(TReq req, Func<Task<TRes>> next, CancellationToken ct)
    {
        if (req is IComplianceCheckable c)
        {
            var hits = await _rules.ScanAsync(c.TextFields);      // 敏感词 + 语义规则
            if (hits.Any(h => h.Level == RiskLevel.Block))
                throw new ComplianceBlockedException(hits);        // 阻断 + 返回改写建议
            if (hits.Any())
                await _alerts.RecordAsync(c.TenantId, hits);       // 软提示但留痕
        }
        return await next();
    }
}

// 硬规则：化学农药不得对个人零售
public sealed class SupplySpuValidator : AbstractValidator<CreateSupplySpuCommand>
{
    public SupplySpuValidator()
    {
        RuleFor(x => x.Category).NotEqual(SupplyCategory.Pesticide)
            .When(x => x.RetailAllowed)
            .WithMessage("化学农药禁止对个人零售，请改为「农场代施服务」并绑定工单");
        RuleFor(x => x.BatchNo).NotEmpty().When(x => x.NeedTrace)
            .WithMessage("需留痕类目必须登记批次号");
    }
}
```

### B6 · SignalR Hub（IoT 实时 + 通知）

```csharp
[Authorize]
public sealed class FarmHub : Hub
{
    public Task WatchAsset(long assetId)          => Groups.AddToGroupAsync(Context.ConnectionId, $"asset:{assetId}");
    public Task UnwatchAsset(long assetId)        => Groups.RemoveFromGroupAsync(Context.ConnectionId, $"asset:{assetId}");
}

// IoT 侧推送（MqttWorker 内）
await _hub.Clients.Group($"asset:{assetId}")
    .SendAsync("telemetry", new { deviceId, metric, value, ts });
```

### B7 · IoT 后台服务（MQTTnet）

```csharp
public sealed class MqttWorker : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken ct)
    {
        var factory = new MqttClientFactory();
        using var client = factory.CreateMqttClient();
        var opt = new MqttClientOptionsBuilder()
            .WithTcpServer(_cfg.Broker, _cfg.Port)
            .WithCredentials(_cfg.User, _cfg.Password)
            .WithTlsOptions(o => o.UseTls())
            .WithClientId($"saas-{Environment.MachineName}")
            .Build();

        client.ApplicationMessageReceivedAsync += async e =>
        {
            var (deviceId, metric, value, ts) = TelemetryCodec.Decode(e.ApplicationMessage.Payload);
            _buffer.Add(new SensorData(deviceId, metric, value, ts));      // 批量写
            if (_threshold.IsExceeded(metric, value))
                await _bus.PublishAsync(new IotThresholdExceededEvent(deviceId, metric, value));
            if (_buffer.Count >= 500) await FlushAsync(ct);
        };

        await client.ConnectAsync(opt, ct);
        await client.SubscribeAsync("tenant/+/device/+/telemetry", ct);
        await Task.Delay(Timeout.Infinite, ct);       // 断连由 UseDisconnectedHandler 重连
    }
}
```

### B8 · Program.cs 装配要点

```csharp
var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddDbContext<SaaSDbContext>(o => o.UseNpgsql(cs, n => n.MigrationsAssembly("Farm.SaaS.Infrastructure")))
    .AddStackExchangeRedisCache(o => o.Configuration = redis)
    .AddCap(x => { x.UseEntityFramework<SaaSDbContext>(); x.UseRabbitMQ(rb); x.UseDashboard(); })
    .AddHangfire(h => h.UsePostgreSqlStorage(cs)).AddHangfireServer()
    .AddSignalR()
    .AddHealthChecks().AddNpgsql(cs).AddRedis(redis);

builder.Services.AddScoped<ICurrentTenant, CurrentTenant>();
builder.Services.AddTenantResolver();          // 域名 → Header → JWT Claim
builder.Services.AddJwtAuthentication(builder.Configuration);
builder.Services.AddScoped(typeof(ICommandPipeline<,>), typeof(ComplianceBehavior<,>));

var app = builder.Build();
app.UseMiddleware<TenantMiddleware>()          // 解析租户并写入 AsyncLocal
   .UseMiddleware<ExceptionHandlingMiddleware>()
   .UseAuthentication().UseAuthorization()
   .UseSerilogRequestLogging();
app.MapControllers(); app.MapHub<FarmHub>("/hubs/farm"); app.MapHangfireDashboard("/jobs");
app.MapHealthChecks("/health");
RecurringJob.AddOrUpdate<OverdueSelfTaskJob>("overdue-scan", j => j.RunAsync(), "*/30 * * * *");
app.Run();
```

## 附录 C：第三方对接清单与工期

| # | 能力 | 候选 | .NET 支持 | 兜底工期 |
|---|---|---|---|---|
| 1 | 支付 + 分账 | 微信支付服务商模式 | 有社区库 / REST | 2 天 |
| 2 | 电子合同 | e签宝 / 法大大 | OpenAPI | 2 天 |
| 3 | IoT | EMQX | 标准 MQTT，MQTTnet ✅ | — |
| 4 | 物流轨迹 | 快递鸟 / 快递100 | 纯 HTTP | 1 天 |
| 5 | 短信 | 阿里云 / 腾讯云 | 官方 SDK ✅ | — |
| 6 | 对象存储 | OSS / COS | 官方 SDK ✅ | — |
| 7 | 直播（二期） | SRS / 云直播 | 标准协议 ✅ | — |
| 8 | 农险（二期） | 合作机构 | 多无 SDK | 人工出单 + 系统登记 |

**合计兜底 ≈ 5 人日，无阻塞性障碍。W1 必须完成核实。**

## 附录 D：名词表

| 名词 | 含义 |
|---|---|
| 认养关系 Adoption | 订单支付后生成的"用户 × 实体资产 × 周期"绑定关系 |
| 托管模式 CareMode | full 全托管 / semi 半托管 / self 不托管 |
| 责任矩阵 DutyMatrix | 13 个农事环节 → 执行方（farm / customer）的映射，JSONB |
| 代管服务工单 ServiceOrder | 自管任务逾期转成的付费工单，分账 70/20/10 |
| 投入品 InputRecord | 农药肥料等使用记录，来源分 farm / customer，合并进溯源 |
| 哈希链 | 生长档案逐条哈希串联，防篡改；二期定期锚定到区块链 |
| 合规健康分 | 租户级合规评分（资质/资金/合同/文案/溯源），可导出报告 |

---

> **给研发的第一条提醒**：这套系统的难点不在并发，在**业务规则的正确性**（三档责任边界、分账恒等、幂等支付、租户隔离）。请把领域层单测（价格计算、责任矩阵判定、分账拆分、哈希链）当成一等公民，覆盖率低于 70% 不许进入试点。
