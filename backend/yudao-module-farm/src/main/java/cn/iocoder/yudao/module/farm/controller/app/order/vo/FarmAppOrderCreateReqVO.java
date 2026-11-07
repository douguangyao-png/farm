package cn.iocoder.yudao.module.farm.controller.app.order.vo;

import lombok.Data;

/** 认养下单（mock：farm-app.ts 61 行；契约 4 节 POST /farm/order/create） */
@Data
public class FarmAppOrderCreateReqVO {
    private Long productId;
    /** full / semi / self */
    private String careMode;
    /** 必须为 true，否则 1_030_000_001 */
    private Boolean contractAgreed;
    private String receiverName;
    private String receiverMobile;
    private String address;
    /** 交付方式 cold / pickup / monthly；演示口径，只接收不落库（订单表暂无该列） */
    private String deliverWay;
}
