package cn.iocoder.yudao.module.farm.controller.admin.notice.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 公告列表项：{ id, title, introduction, createTime, categoryName } */
@Data
public class FarmNoticeListRespVO {
    private Long id;
    private String title;
    private String introduction;
    private LocalDateTime createTime;
    private String categoryName;
}
