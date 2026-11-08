package cn.iocoder.yudao.module.farm.controller.admin.harvest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 对应 mock harvestView；harvestDate 与 mock 一致，返回当天 0 点的毫秒时间戳 */
@Data
public class FarmHarvestRespVO {
    private Long id;
    private Long adoptionId;
    private Long assetId;
    private String assetCode;
    private String memberName;
    private String crop;
    private BigDecimal weightJin;
    private Long harvestDate;
    private List<String> photos;
    private String remark;
    private Long shipmentId;
    /** 环境快照 {temp,humidity,soilMoisture,light,capturedAt,source} */
    private java.util.Map<String, Object> envSnapshot;
}
