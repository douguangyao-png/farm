import { getDb, type Order, type Adoption, type GrowthRecord, type Task, type ServiceOrder, type SupplyOrder, type Harvest, type Shipment, type Product, type Asset, type Supply } from '../store.js'
import { CARE_MODE_NAME, maskMobile, envAt } from './common.js'

/** 把库里的记录拼成接口返回的样子（字段见 docs/api/farm-接口契约.md）。后台和 C 端共用，保证同一实体字段一致。 */

const db = () => getDb()
const member = (id: number | null | undefined) => db().members.find((m) => m.id === id)
const user = (id: number | null | undefined) => db().users.find((u) => u.id === id)
const asset = (id: number | null | undefined) => db().assets.find((a) => a.id === id)
const base = (id: number | null | undefined) => db().bases.find((b) => b.id === id)
const product = (id: number | null | undefined) => db().products.find((p) => p.id === id)

/** 资产类型 → 认养分类名（原型 08 的「分类」列）；工作台认养位、本周经营概览也用 */
export const CATEGORY_NAME: Record<string, string> = { plot: '土地认养', animal: '家禽认养', tree: '果树认养' }

export function productView(p: Product) {
  const enabled = p.careModes.filter((m) => m.enabled)
  const main = enabled.find((m) => m.mode === 'full') ?? enabled[0]
  const totalSlots = p.stock + p.sold
  return {
    ...p,
    baseName: base(p.baseId)?.name ?? '',
    minPrice: Math.min(...enabled.map((m) => m.price)),
    enabledModes: enabled.map((m) => m.mode),
    // 以下是对齐原型 goodsList 补的列，全部由现有数据派生
    no: 'P' + String(p.id).padStart(3, '0'),
    categoryName: CATEGORY_NAME[p.assetType] ?? '',
    totalSlots,
    soldRate: totalSlots > 0 ? Math.round((p.sold / totalSlots) * 100) : 0,
    insured: enabled.some((m) => m.insurance.includes('含')),
    expectedOutput: main?.outputPromise ?? ''
  }
}

export function assetView(a: Asset) {
  const ad = db().adoptions.find((x) => x.assetId === a.id && x.status === 10)
  return {
    ...a,
    baseName: base(a.baseId)?.name ?? '',
    adoptionId: ad?.id ?? null,
    memberName: ad ? member(ad.memberId)?.nickname ?? '' : '',
    careMode: ad?.careMode ?? null
  }
}

export function contractOf(o: Order) {
  return {
    no: 'HT' + o.no.slice(2),
    version: o.contractVersion,
    title: `《认养服务合同（${CARE_MODE_NAME[o.careMode]}）》${o.contractVersion}`,
    agreed: o.contractAgreed,
    // 手写签名后以签名时间为准；没签名的老数据沿用支付时间
    signedAt: o.signedAt ?? o.payTime,
    hasSignature: !!o.signature
  }
}

/** 合同状态：10 已签署 / 0 待签署 / 20 已解除（订单已取消）。合同台账由订单派生 */
export function contractStatusOf(o: Order): number {
  if (o.status === 30 || o.status === 50) return 20
  return o.contractAgreed && o.payTime ? 10 : 0
}

export function orderView(o: Order) {
  const m = member(o.memberId)
  const p = product(o.productId)
  const ad = o.adoptionId ? db().adoptions.find((a) => a.id === o.adoptionId) : undefined
  const promisedJin = ad?.promisedJin ?? null
  const harvestedJin = ad?.harvestedJin ?? 0
  const { signature: _sig, ...rest } = o // 签名图片不放进订单视图
  return {
    ...rest,
    // 第一期补齐的订单字段：老订单没有时给缺省值
    channel: o.channel ?? 'h5',
    packageName: o.packageName ?? `${CARE_MODE_NAME[o.careMode]}${p ? ' · ' + (p.cycleDays >= 360 ? '一年' : p.cycleDays + ' 天') : ''}`,
    couponName: o.couponName ?? null,
    couponAmount: o.couponAmount ?? 0,
    gpsLng: o.gpsLng ?? null,
    gpsLat: o.gpsLat ?? null,
    gpsText: o.gpsText ?? null,
    deliverWay: o.deliverWay ?? 'express',
    hasSignature: !!o.signature,
    signedAt: o.signedAt ?? null,
    // 演示口径：支付方式固定微信支付；履约进度 = 已收 / 约定交付（百分比整数，0-100）
    payMethod: '微信支付',
    promisedJin,
    harvestedJin,
    progress: promisedJin ? Math.min(100, Math.round((harvestedJin / promisedJin) * 100)) : 0,
    memberName: m?.nickname ?? '',
    memberMobile: m?.mobile ?? '',
    productTitle: p?.title ?? '',
    productCover: p?.cover ?? '',
    assetCode: asset(o.assetId)?.code ?? '',
    careModeName: CARE_MODE_NAME[o.careMode]
  }
}

export function adoptionView(ad: Adoption) {
  const p = product(ad.productId)
  const a = asset(ad.assetId)
  const selfTodoCount = db().tasks.filter((t) => t.adoptionId === ad.id && t.owner === 'member' && t.status === 0).length
  return {
    ...ad,
    productTitle: p?.title ?? '',
    cover: p?.cover ?? '',
    assetCode: a?.code ?? '',
    assetType: a?.assetType ?? '',
    baseName: base(a?.baseId)?.name ?? '',
    memberName: member(ad.memberId)?.nickname ?? '',
    careModeName: CARE_MODE_NAME[ad.careMode],
    selfTodoCount
  }
}

export function adoptionDetail(ad: Adoption) {
  const a = asset(ad.assetId)!
  const p = product(ad.productId)
  const st = user(ad.stewardId)
  return {
    ...adoptionView(ad),
    asset: { id: a.id, code: a.code, name: a.name, crop: a.specs.crop ?? a.specs.breed ?? '', areaM2: a.specs.areaM2 ?? null, cameraName: a.cameraName, assetType: a.assetType },
    steward: st ? { name: st.nickname, mobile: maskMobile(st.mobile) } : null,
    careModeInfo: p?.careModes.find((m) => m.mode === ad.careMode) ?? null
  }
}

const RECORD_KIND_NAME: Record<string, string> = { farming: '农事', harvest: '采收', service: '代管服务' }

export function recordView(r: GrowthRecord) {
  const ad = db().adoptions.find((x) => x.id === r.adoptionId)
  // 机器校验：三项都通过 = ok，否则 suspect 并给出一句原因（checks 里 true 表示通过）
  const notes: string[] = []
  if (r.checks && !r.checks.exifTime) notes.push('拍摄时间偏差')
  if (r.checks && !r.checks.gpsFence) notes.push('地点偏移')
  if (r.checks && !r.checks.duplicate) notes.push('画面重复率高')
  return {
    ...r,
    assetCode: asset(r.assetId)?.code ?? '',
    memberName: ad ? member(ad.memberId)?.nickname ?? '' : '',
    stewardName: user(r.stewardId)?.nickname ?? '',
    kindName: RECORD_KIND_NAME[r.kind] ?? '',
    summary: `${RECORD_KIND_NAME[r.kind] ?? ''} · ${r.title}`,
    photoCount: r.photos?.length ?? 0,
    machineCheck: notes.length ? 'suspect' : 'ok',
    machineCheckNote: notes.join('、')
  }
}

export function taskView(t: Task) {
  const a = asset(t.assetId)
  const ad = db().adoptions.find((x) => x.id === t.adoptionId)
  return {
    ...t,
    assetCode: a?.code ?? '',
    baseName: base(a?.baseId)?.name ?? '',
    memberName: ad ? member(ad.memberId)?.nickname ?? '' : '',
    careMode: ad?.careMode ?? null,
    overdue: t.status === 0 && t.deadline < Date.now()
  }
}

export function serviceOrderView(s: ServiceOrder) {
  return {
    ...s,
    assetCode: asset(s.assetId)?.code ?? '',
    memberName: member(s.memberId)?.nickname ?? '',
    stewardName: user(s.stewardId)?.nickname ?? ''
  }
}

export function supplyView(s: Supply) {
  return s
}

export function supplyOrderView(o: SupplyOrder) {
  const spu = db().supplies.find((x) => x.id === o.spuId)
  return {
    ...o,
    memberName: member(o.memberId)?.nickname ?? '',
    assetCode: asset(o.assetId)?.code ?? '',
    spuName: spu?.name ?? '',
    cat: spu?.cat ?? '',
    cover: spu?.cover ?? '',
    unit: spu?.unit ?? ''
  }
}

/** 收成环境快照：创建收成时按资产当时的环境数据写入；老数据（种子）按采收日的环境数据补，标「演示数据」 */
export function makeEnvSnapshot(a: Asset | undefined, ts: number) {
  if (!a) return null
  const e = envAt(a, ts)
  return { temp: e.airTemp, humidity: e.airHumidity, soilMoisture: e.soilMoisture, light: e.light, capturedAt: ts, source: '演示数据' }
}

export function harvestView(h: Harvest) {
  const ad = db().adoptions.find((x) => x.id === h.adoptionId)
  return { ...h, envSnapshot: h.envSnapshot ?? makeEnvSnapshot(asset(h.assetId), h.harvestDate), assetCode: asset(h.assetId)?.code ?? '', memberName: ad ? member(ad.memberId)?.nickname ?? '' : '' }
}

export function shipmentView(s: Shipment) {
  return { ...s, memberName: member(s.memberId)?.nickname ?? '', assetCode: asset(db().adoptions.find((x) => x.id === s.adoptionId)?.assetId)?.code ?? '' }
}
