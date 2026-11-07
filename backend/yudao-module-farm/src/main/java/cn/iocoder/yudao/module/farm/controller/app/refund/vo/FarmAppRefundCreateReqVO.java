package cn.iocoder.yudao.module.farm.controller.app.refund.vo;

import lombok.Data;

@Data
public class FarmAppRefundCreateReqVO {
    private Long orderId;
    /** 退款原因，必填，最长 200 字 */
    private String reason;
}
