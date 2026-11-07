package cn.iocoder.yudao.module.farm.controller.admin.platform.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmRiskAlertPageReqVO extends PageParam {
    private Long tenantId;
    private String type;
    private Integer status;
}
