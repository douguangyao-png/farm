package cn.iocoder.yudao.module.farm.controller.admin.feedback.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FarmFeedbackRespVO {
    private Long id;
    private String no;
    private Long tenantId;
    private String tenantName;
    private String type;
    private String content;
    private List<String> screenshots;
    private String pageTitle;
    private String pagePath;
    private String userAgent;
    private Long creatorId;
    private String creatorName;
    private Integer status;
    /** [{ id, content, creatorName, createTime(毫秒时间戳) }] */
    private List<Map<String, Object>> replies;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
