package cn.iocoder.yudao.module.farm.controller.admin.steward.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应 mock taskView */
@Data
public class FarmStewardTaskRespVO {
    private Long id;
    private Long adoptionId;
    private Long assetId;
    private String assetCode;
    private String baseName;
    private String memberName;
    private String careMode;
    private String owner;
    private Long stewardId;
    private String title;
    private String description;
    /** calendar / iot / manual / service */
    private String source;
    private Long serviceOrderId;
    private LocalDateTime deadline;
    private Integer status;
    private Long entrustPrice;
    /** 待办且已过截止时间 */
    private Boolean overdue;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
}
