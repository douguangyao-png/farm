package cn.iocoder.yudao.module.farm.controller.admin.article.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmArticlePageReqVO extends PageParam {
    private String title;
    private String author;
    private Long categoryId;
    private Integer status;
}
