package cn.iocoder.yudao.module.farm.controller.app.selftask.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** C 端自管任务（mock：farm-app.ts 203–206 行） */
@Data
public class FarmAppSelfTaskRespVO {
    private Long id;
    private Long adoptionId;
    private String assetCode;
    private String title;
    private String description;
    private LocalDateTime deadline;
    /** 0 待办、10 已完成、20 已委托代办 */
    private Integer status;
    /** 待办且已过截止时间 */
    private Boolean overdue;
    /** 委托代办价（分） */
    private Long entrustPrice;
    private LocalDateTime finishTime;
    private Long serviceOrderId;
}
