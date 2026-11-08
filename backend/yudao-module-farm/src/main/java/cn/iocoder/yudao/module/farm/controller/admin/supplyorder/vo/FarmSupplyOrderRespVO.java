package cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应 mock 的 supplyOrderView */
@Data
public class FarmSupplyOrderRespVO {
    private Long id;
    private Long tenantId;
    private String no;
    private Long memberId;
    private Long adoptionId;
    private Long assetId;
    private Long spuId;
    private Integer qty;
    private Long amount;
    private String deliverWay;
    private String batchNo;
    private Integer traceState;
    private LocalDateTime usedAt;
    private String dosage;
    /** 收货信息：deliverWay=express 时必填，其它方式可空 */
    private String receiverName;
    private String receiverMobile;
    private String address;
    private LocalDateTime createTime;
    private String memberName;
    private String assetCode;
    private String spuName;
    private String cat;
    private String cover;
    private String unit;
    /** 订单状态：待使用 / 已使用 / 已安装 / 待派单 / 已完成 / 已生效（演示口径，见 FarmSupplyOrderService.stateName） */
    private String stateName;
}
