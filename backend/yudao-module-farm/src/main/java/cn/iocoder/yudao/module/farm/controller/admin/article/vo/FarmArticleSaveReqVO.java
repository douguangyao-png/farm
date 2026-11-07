package cn.iocoder.yudao.module.farm.controller.admin.article.vo;

import lombok.Data;

/** 文章新增 / 修改。必填校验放在 Service 里，以便返回 400 + 中文提示 */
@Data
public class FarmArticleSaveReqVO {
    private Long id;
    private Long categoryId;
    private String title;
    private String author;
    private String picUrl;
    private String introduction;
    private String content;
    private Integer sort;
    private Integer status;
    private Boolean recommendHot;
    private Boolean recommendBanner;
    private Long spuId;
}
