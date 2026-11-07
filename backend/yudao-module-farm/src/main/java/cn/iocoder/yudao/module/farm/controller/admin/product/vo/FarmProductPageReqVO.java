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
    /** 认养分类：plot / animal / tree */
    private String assetType;
    /** 价格区间（分，按全托管价 basePrice 过滤，含边界） */
    private Long priceMin;
    private Long priceMax;
}
