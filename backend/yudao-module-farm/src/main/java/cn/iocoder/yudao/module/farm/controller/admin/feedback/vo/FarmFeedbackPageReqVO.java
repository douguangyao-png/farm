package cn.iocoder.yudao.module.farm.controller.admin.feedback.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmFeedbackPageReqVO extends PageParam {
    private String type;
    private Integer status;
    private String no;
}
