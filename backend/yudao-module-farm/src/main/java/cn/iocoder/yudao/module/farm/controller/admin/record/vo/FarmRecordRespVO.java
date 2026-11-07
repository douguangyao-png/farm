package cn.iocoder.yudao.module.farm.controller.admin.record.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 对应 mock recordView */
@Data
public class FarmRecordRespVO {
    private Long id;
    private Long adoptionId;
    private Long assetId;
    private String assetCode;
    private String memberName;
    private Long stewardId;
    private String stewardName;
    private Long taskId;
    /** farming / harvest / service */
    private String kind;
    private String title;
    private String content;
    private List<String> photos;
    private BigDecimal lng;
    private BigDecimal lat;
    /** { airTemp, airHumidity, soilMoisture } */
    private Map<String, Object> env;
    /** { exifTime, gpsFence, duplicate } */
    private Map<String, Object> checks;
    private Integer status;
    private String auditRemark;
    private LocalDateTime createTime;
    private LocalDateTime auditTime;
}
