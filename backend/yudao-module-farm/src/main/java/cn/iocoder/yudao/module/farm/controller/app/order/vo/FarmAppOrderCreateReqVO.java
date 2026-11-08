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
    /** 交付方式 express 快递到家 / pickup 到基地自提 / farm_deliver 基地配送；缺省 express，其它值 400 */
    private String deliverWay;
    /** 来源渠道 mini/h5/scan/steward；缺省 h5，其它值 400 */
    private String channel;
    private String packageName;
    private java.math.BigDecimal lng;
    private java.math.BigDecimal lat;
    private String gpsText;
}
