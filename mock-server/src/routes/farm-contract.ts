import { Router } from 'express'
import { getDb } from '../store.js'
import { ok, requireLogin } from '../http.js'
import { page, q, like, eqNum, byNewest, tenantOf } from '../farm/common.js'
import { contractOf, contractStatusOf, orderView } from '../farm/views.js'

/** /admin-api/farm/contract/**：合同管理（契约 2.5.1）。台账由订单派生，只读；模板库在前端写死（演示数据） */
export const farmContractRouter = Router()
farmContractRouter.use(requireLogin('admin'))

farmContractRouter.get('/page', (req, res) => {
  const t = tenantOf(req)
  // 合同编号 = 'HT' + 订单号去掉前两位；按编号查时去掉 HT 前缀，匹配订单号
  const no = q(req, 'no')?.replace(/^HT/i, '')
  const rows = getDb().orders.filter((o) => o.tenantId === t)
    .map((o) => {
      const ov = orderView(o)
      const c = contractOf(o)
      const status = contractStatusOf(o)
      return {
        id: o.id,
        no: c.no,
        orderId: o.id,
        orderNo: o.no,
        memberName: ov.memberName,
        memberMobile: ov.memberMobile,
        productTitle: ov.productTitle,
        careMode: o.careMode,
        careModeName: ov.careModeName,
        version: c.version,
        title: c.title,
        status,
        signMethod: '电子签名',
        // 演示口径：已签署的合同都标 CA 认证、已上链（真实 CA/区块链存证不在本期范围）
        caAuth: status === 10,
        chainProof: status === 10,
        signedAt: status === 10 ? c.signedAt : null,
        createTime: o.createTime
      }
    })
    .filter((r) => like(r.orderNo, no) && like(r.memberMobile, q(req, 'memberMobile')) && eqNum(r.status, q(req, 'status')))
    .sort(byNewest)
  return ok(res, page(req, rows))
})
