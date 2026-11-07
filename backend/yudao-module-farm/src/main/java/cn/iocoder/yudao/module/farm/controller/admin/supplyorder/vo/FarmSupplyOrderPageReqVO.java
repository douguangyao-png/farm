package cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmSupplyOrderPageReqVO extends PageParam {
    /** 0 待登记 / 10 已登记 */
    private Integer traceState;
    private String no;
}
