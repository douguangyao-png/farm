package cn.iocoder.yudao.module.farm.controller.admin.notice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 公告详情：文章全部字段 + categoryName（mock：{ ...article, categoryName }） */
@Data
public class FarmNoticeRespVO {
    private Long id;
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
