/** 农事记录审核页专用枚举（对齐原型 08 archivePub；共享 constants.ts 里的 RECORD_STATUS 不改） */
import type { EnumItem } from '../constants'

/** 原型状态文案：待审核 / 已发布（通过即发布到 C 端「生长档案」） / 已驳回 */
export const RECORD_PUBLISH_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待审核', type: 'warning' },
  { value: 10, label: '已发布', type: 'success' },
  { value: 20, label: '已驳回', type: 'danger' }
]

/** 机器校验结果（接口字段 machineCheck） */
export const MACHINE_CHECK: EnumItem<string>[] = [
  { value: 'ok', label: '正常', type: 'success' },
  { value: 'suspect', label: '存疑', type: 'danger' }
]
