package cn.iocoder.yudao.module.farm.controller.admin.base.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmBasePageReqVO extends PageParam {
    private String name;
    private Integer status;
}
