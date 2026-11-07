/** 农事类型（原型 08 收成录入页的 7 个标签；录入收成时默认「采摘采收」） */
export const FARM_TYPES = ['播种', '施肥', '病虫害防治', '浇水', '搭架整枝', '采摘采收', '翻耕']

/** 保存后的联动：implemented=false 的是演示说明，暂未接入真实能力 */
export const LINKAGES: { title: string; desc: string; implemented: boolean }[] = [
  {
    title: '写入生长档案',
    desc: 'C 端「我的认养 - 生长档案」新增一条采收记录（直接算已审核）',
    implemented: true
  },
  {
    title: '更新累计产出',
    desc: '认养的「已收斤数」累加本批合计重量，C 端产出进度条同步变化',
    implemented: true
  },
  {
    title: '进入配送',
    desc: '保存后到「配送批次」点新建批次填物流单号发货，同一天发出的配送单归为一个批次',
    implemented: true
  },
  {
    title: '追加溯源履历 / 推送认养人',
    desc: '区块链存证和订阅消息推送为演示说明，暂未接入',
    implemented: false
  }
]
