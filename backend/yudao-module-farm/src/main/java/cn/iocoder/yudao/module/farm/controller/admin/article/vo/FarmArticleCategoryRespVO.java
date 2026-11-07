package cn.iocoder.yudao.module.farm.controller.admin.article.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FarmArticleCategoryRespVO {
    private Long id;
    private Long tenantId;
    private String name;
    private String picUrl;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
}
