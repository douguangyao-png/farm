package cn.iocoder.yudao.module.farm.controller.admin.stewardmanage.vo;

import lombok.Data;

/** 管家管理一行（mock：routes/farm-people.ts buildStewards）。id 是芋道后台用户编号 */
@Data
public class FarmStewardManageRespVO {
    private Long id;
    private String name;
    /** 已脱敏 */
    private String mobile;
    private String roleName;
    private Long baseId;
    private String baseName;
    private Integer assetTotal;
    private Integer assetAdopted;
    private Integer memberCount;
    private Integer monthTaskTotal;
    private Integer monthTaskDone;
    /** 百分比整数；本月没有任务时 null */
    private Integer monthTaskRate;
    /** 分钟；没有接单记录时 null */
    private Long avgResponseMinutes;
    /** 0 在岗（用户表没有在岗字段，演示口径） */
    private Integer status;
}
