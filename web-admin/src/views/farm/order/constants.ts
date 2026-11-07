/** 订单页自己用的常量（共享的 ../constants.ts 不动） */

/** 订单页签：原型 orderList 的五个页签。value 为空 = 全部；40 退款中（退款闭环未做，暂无数据） */
export const ORDER_TABS: { value: number | undefined; label: string }[] = [
  { value: undefined, label: '全部' },
  { value: 0, label: '待付款' },
  { value: 10, label: '认养中' },
  { value: 20, label: '已完成' },
  { value: 40, label: '退款中' }
]
