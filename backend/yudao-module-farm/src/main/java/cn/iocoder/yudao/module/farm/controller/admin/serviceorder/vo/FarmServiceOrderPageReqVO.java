package cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmServiceOrderPageReqVO extends PageParam {
    private Integer status;
    /** 工单号，模糊匹配 */
    private String no;
}
