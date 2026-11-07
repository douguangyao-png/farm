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
    private LocalDateTime createTime;
    private String memberName;
    private String assetCode;
    private String spuName;
    private String cat;
    private String cover;
    private String unit;
}
