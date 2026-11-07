package cn.iocoder.yudao.module.farm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 退款闭环错误码（第二期 A）。与 mock-server/src/routes/farm-refund.ts 的 REFUND_BIZ 一致。
 * 登记文件里建议主线程并入 ErrorCodeConstants 后删除本文件；在此之前单独存在，不改共享文件。
 */
public interface FarmRefundErrorCodes {

    /** 订单已有退款申请在处理中 / 已退款 */
    ErrorCode REFUND_DUPLICATE = new ErrorCode(1_030_000_008, "{}");
    /** 订单状态不允许申请退款（只有「认养中」可申请） */
    ErrorCode REFUND_NOT_ALLOWED = new ErrorCode(1_030_000_009, "{}");
    /** 退款单状态已变化（已被审核） */
    ErrorCode REFUND_STATE_CHANGED = new ErrorCode(1_030_000_010, "退款单状态已变化，请刷新");
}
