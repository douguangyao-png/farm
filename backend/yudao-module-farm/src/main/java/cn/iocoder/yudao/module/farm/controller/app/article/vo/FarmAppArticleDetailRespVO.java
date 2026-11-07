package cn.iocoder.yudao.module.farm.controller.app.article.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** C 端农场资讯详情（mock：farm-app.ts 286–292 行）：文章全部字段 + categoryName */
@Data
public class FarmAppArticleDetailRespVO {
    private Long id;
    private Long tenantId;
    private Long categoryId;
    private String categoryName;
    private String title;
    private String author;
    private String picUrl;
    private String introduction;
    private String content;
    private Integer sort;
    private Integer status;
    private Integer browseCount;
    private Boolean recommendHot;
    private Boolean recommendBanner;
    private Long spuId;
    private LocalDateTime createTime;
}
