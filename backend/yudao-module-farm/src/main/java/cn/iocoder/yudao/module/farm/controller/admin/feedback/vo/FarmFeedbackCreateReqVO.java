package cn.iocoder.yudao.module.farm.controller.admin.feedback.vo;

import lombok.Data;

import java.util.List;

@Data
public class FarmFeedbackCreateReqVO {
    private String type;
    private String content;
    private List<String> screenshots;
    private String pageTitle;
    private String pagePath;
    private String userAgent;
}
