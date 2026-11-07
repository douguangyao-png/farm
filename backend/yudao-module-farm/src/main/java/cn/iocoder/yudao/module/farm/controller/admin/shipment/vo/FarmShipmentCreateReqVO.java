package cn.iocoder.yudao.module.farm.controller.admin.shipment.vo;

import lombok.Data;

@Data
public class FarmShipmentCreateReqVO {
    private Long harvestId;
    private String carrier;
    private String logisticsNo;
    /** 不传按冷链处理（mock：coldChain !== false） */
    private Boolean coldChain;
}
