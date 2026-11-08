package cn.iocoder.yudao.module.farm.controller.admin.shipbatch.vo;

import lombok.Data;

import java.util.List;

@Data
public class FarmShipBatchCreateReqVO {
    private Long harvestId;
    /** 计划发货日期，毫秒时间戳（按北京时间取日期） */
    private Long planDate;
    private String remark;
    private List<Long> shipmentIds;
}
