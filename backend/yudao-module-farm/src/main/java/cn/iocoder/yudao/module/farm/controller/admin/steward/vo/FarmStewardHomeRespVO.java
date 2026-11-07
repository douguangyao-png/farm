package cn.iocoder.yudao.module.farm.controller.admin.steward.vo;

import lombok.Data;

/** 管家端首页（契约 3；mock farm-steward.ts 15–34 行） */
@Data
public class FarmStewardHomeRespVO {
    private String stewardName;
    private String baseName;
    private Todo todo;
    private Integer todayDone;
    private Integer monthDone;
    /** 自己提交待审核的记录数 */
    private Long pendingAuditCount;
    /** 近 7 天被驳回数 */
    private Long rejectedCount;

    @Data
    public static class Todo {
        private Integer taskCount;
        private Integer overdueCount;
        private Integer serviceOrderPending;
        private Long serviceOrderMine;
    }
}
