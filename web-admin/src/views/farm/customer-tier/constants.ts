/**
 * 客户分层页自己用的常量。
 * 权限串（第二期权限清单「A」小节）：
 *   farm:customertier:query   查看分层、到期提醒、人群包
 *   farm:customertier:create  新建人群包（演示，暂未实现；人群包是只读规则圈选）
 *   farm:customertier:push    推送（演示，暂未实现；营销推送不在本期范围）
 */

/** 到期前提醒节点说明（原型 08 custGroup） */
export const REMIND_HINT =
  '系统会在到期前 30/15/3 天分别提醒经营者跟进续费，续费可保留原地块编号优先预约（提醒与优先预约为演示说明，本期不推送）。'

export const EXPIRE_NOTE = '到期未续费的地块自动进入「空闲池」，可重新上架销售。'
