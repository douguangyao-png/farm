package cn.iocoder.yudao.module.farm.controller.admin.platform.vo;

import lombok.Data;

@Data
public class FarmRiskAlertHandleReqVO {
    private Long id;
    /** 10 已处理、20 已忽略 */
    private Integer status;
    private String handleRemark;
}
