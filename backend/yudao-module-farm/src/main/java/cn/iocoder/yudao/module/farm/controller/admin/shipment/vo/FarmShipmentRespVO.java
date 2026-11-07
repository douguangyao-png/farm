package cn.iocoder.yudao.module.farm.controller.admin.shipment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 对应 mock shipmentView */
@Data
public class FarmShipmentRespVO {
    private Long id;
    private String no;
    private Long harvestId;
    private Long adoptionId;
    private Long memberId;
    private String memberName;
    private String assetCode;
    private String receiverName;
    private String receiverMobile;
    private String address;
    private BigDecimal weightJin;
    private String carrier;
    private String logisticsNo;
    private Boolean coldChain;
    /** 0 待发货、10 已发货（原名运输中）、20 已完成（原名已签收） */
    private Integer status;
    /** [{ time(毫秒时间戳), content }] */
    private List<Map<String, Object>> tracks;
    private LocalDateTime createTime;
    /** 批次号：B + 创建日期，同一天发的配送单算一个批次，例 B2026-1107 */
    private String batchNo;
    /** 计划日期 = 创建当天 0 点的毫秒时间戳 */
    private Long planDate;
    /** 完成度 %：待发货 28 / 已发货 62 / 已完成 100 */
    private Integer progress;
}
