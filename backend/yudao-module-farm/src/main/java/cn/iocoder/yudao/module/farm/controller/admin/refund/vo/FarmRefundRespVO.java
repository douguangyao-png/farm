package cn.iocoder.yudao.module.farm.controller.admin.refund.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 退款单（后台、C 端共用，字段与 mock refundView 一致）。金额单位：分 */
@Data
public class FarmRefundRespVO {
    private Long id;
    private String no;
    private Long orderId;
    private String orderNo;
    private Integer orderStatus;
    private Long memberId;
    private String memberName;
    private String memberMobile;
    private String productTitle;
    private String productCover;
    private String assetCode;
    private String careMode;
    private String careModeName;
    private Long amount;
    private Long investDeduct;
    private Long deliverDeduct;
    private Long deduct;
    private Long refundAmount;
    private String reason;
    private Integer status;
    private String statusName;
    private String auditRemark;
    private Integer prevOrderStatus;
    private LocalDateTime createTime;
    private LocalDateTime auditTime;
}
