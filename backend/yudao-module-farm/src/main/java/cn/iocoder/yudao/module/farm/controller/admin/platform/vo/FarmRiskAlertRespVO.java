package cn.iocoder.yudao.module.farm.controller.admin.platform.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FarmRiskAlertRespVO {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String type;
    /** 1 低 2 中 3 高 */
    private Integer level;
    private String title;
    private String content;
    private String source;
    private Integer status;
    private String handleRemark;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
