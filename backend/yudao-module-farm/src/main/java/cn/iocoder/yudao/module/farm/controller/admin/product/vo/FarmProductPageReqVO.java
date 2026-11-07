package cn.iocoder.yudao.module.farm.controller.admin.product.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmProductPageReqVO extends PageParam {
    private String title;
    private Integer status;
    private Long baseId;
}
