import { getDb, type Order, type Adoption, type GrowthRecord, type Task, type ServiceOrder, type SupplyOrder, type Harvest, type Shipment, type Product, type Asset, type Supply } from '../store.js'
import { CARE_MODE_NAME, maskMobile } from './common.js'

/** 把库里的记录拼成接口返回的样子（字段见 docs/api/farm-接口契约.md）。后台和 C 端共用，保证同一实体字段一致。 */

const db = () => getDb()
const member = (id: number | null | undefined) => db().members.find((m) => m.id === id)
const user = (id: number | null | undefined) => db().users.find((u) => u.id === id)
const asset = (id: number | null | undefined) => db().assets.find((a) => a.id === id)
const base = (id: number | null | undefined) => db().bases.find((b) => b.id === id)
const product = (id: number | null | undefined) => db().products.find((p) => p.id === id)

export function productView(p: Product) {
  const enabled = p.careModes.filter((m) => m.enabled)
  return {
    ...p,
    baseName: base(p.baseId)?.name ?? '',
    minPrice: Math.min(...enabled.map((m) => m.price)),
    enabledModes: enabled.map((m) => m.mode)
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
    signedAt: o.payTime
  }
}

export function orderView(o: Order) {
  const m = member(o.memberId)
  const p = product(o.productId)
  return {
    ...o,
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

export function recordView(r: GrowthRecord) {
  const ad = db().adoptions.find((x) => x.id === r.adoptionId)
  return {
    ...r,
    assetCode: asset(r.assetId)?.code ?? '',
    memberName: ad ? member(ad.memberId)?.nickname ?? '' : '',
    stewardName: user(r.stewardId)?.nickname ?? ''
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

export function harvestView(h: Harvest) {
  const ad = db().adoptions.find((x) => x.id === h.adoptionId)
  return { ...h, assetCode: asset(h.assetId)?.code ?? '', memberName: ad ? member(ad.memberId)?.nickname ?? '' : '' }
}

export function shipmentView(s: Shipment) {
  return { ...s, memberName: member(s.memberId)?.nickname ?? '', assetCode: asset(db().adoptions.find((x) => x.id === s.adoptionId)?.assetId)?.code ?? '' }
}
