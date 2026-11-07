package cn.iocoder.yudao.module.farm.controller.admin.article.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FarmArticleRespVO {
    private Long id;
    private Long tenantId;
    private Long categoryId;
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
