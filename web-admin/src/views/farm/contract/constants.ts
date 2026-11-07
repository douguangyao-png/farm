/** 合同管理页自己用的常量 */

/** 合同状态（与接口 status 一致） */
export const CONTRACT_STATUS: { value: number; label: string; type: 'success' | 'warning' | 'info' }[] = [
  { value: 10, label: '已签署', type: 'success' },
  { value: 0, label: '待签署', type: 'warning' },
  { value: 20, label: '已解除', type: 'info' }
]

/** 合同模板库（演示数据，静态；原型 08 contractList） */
export const CONTRACT_TEMPLATES = [
  { name: '农产品认养服务合同', scope: '土地 / 果树', version: 'v3.2', updated: '2026-08-01' },
  { name: '畜禽合养服务合同', scope: '畜禽 / 水产', version: 'v3.1', updated: '2026-08-01' },
  { name: '到园体验服务协议', scope: '体验 / 研学', version: 'v2.6', updated: '2026-06-15' },
  { name: '认养权益转赠协议', scope: '全品类', version: 'v1.4', updated: '2026-04-20' }
]

/** 反欺诈条款（所有模板内置，演示数据） */
export const ANTI_FRAUD_CLAUSES = [
  '不承诺收益',
  '不设钱包 / 资金池',
  '不得作为投资凭证',
  '禁止多级分销',
  '实物交付约定',
  '产出浮动免责',
  '保险先行赔付'
]
