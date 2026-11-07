package cn.iocoder.yudao.module.farm.controller.admin.article.vo;

import lombok.Data;

/** 分类新增 / 修改。必填校验放在 Service 里，以便返回 400 + 中文提示 */
@Data
public class FarmArticleCategorySaveReqVO {
    private Long id;
    private String name;
    private String picUrl;
    private Integer sort;
    private Integer status;
}
