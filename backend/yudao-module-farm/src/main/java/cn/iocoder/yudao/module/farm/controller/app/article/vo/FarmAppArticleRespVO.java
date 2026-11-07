package cn.iocoder.yudao.module.farm.controller.app.article.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** C 端农场资讯列表项（mock：farm-app.ts 281–285 行） */
@Data
public class FarmAppArticleRespVO {
    private Long id;
    private String title;
    private String introduction;
    private String picUrl;
    private String author;
    private Integer browseCount;
    private LocalDateTime createTime;
}
