/**
 * 管家管理页自己用的常量。
 * 权限串（第二期权限清单「A」小节）：
 *   farm:stewardmanage:query   查看管家列表、绩效排行
 *   farm:stewardmanage:create  添加管家（演示，暂未实现；真实添加走「系统管理 - 用户」）
 *   farm:stewardmanage:dispatch 派单（演示，暂未实现）
 *   farm:stewardmanage:perf    绩效 / 调整（演示，暂未实现）
 */
import type { EnumItem } from '../constants'

/** 管家状态（用户表没有在岗字段，当前恒为在岗，演示口径） */
export const STEWARD_STATUS: EnumItem<number>[] = [{ value: 0, label: '在岗', type: 'success' }]

/** 管家 App 工作台说明（原型 08 keeperMg 右侧面板，说明性文字） */
export const APP_NOTES: { k: string; v: string }[] = [
  { k: '打卡方式', v: 'App 拍照（强制现场）+ GPS 围栏 + 重复照片校验' },
  { k: '绩效口径', v: '本期只有「月度任务完成率」；平均响应取「代管工单」的接单耗时，满意度暂无评价数据' }
]
