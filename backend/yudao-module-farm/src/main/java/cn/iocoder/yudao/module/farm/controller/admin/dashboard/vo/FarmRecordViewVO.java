package cn.iocoder.yudao.module.farm.controller.admin.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 与 mock recordView 等价：农事记录 + assetCode / memberName / stewardName */
@Data
public class FarmRecordViewVO {
    private Long id;
    private Long tenantId;
    private Long adoptionId;
    private Long assetId;
    private Long stewardId;
    private Long taskId;
    private String kind;
    private String title;
    private String content;
    private List<String> photos;
    private BigDecimal lng;
    private BigDecimal lat;
    private Map<String, Object> env;
    private Map<String, Object> checks;
    private Integer status;
    private String auditRemark;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private String assetCode;
    private String memberName;
    private String stewardName;
}
