package cn.iocoder.yudao.module.farm.controller.admin.article.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmArticleCategoryPageReqVO extends PageParam {
    private String name;
    private Integer status;
}
