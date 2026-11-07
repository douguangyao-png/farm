package cn.iocoder.yudao.module.farm.controller.admin.supply.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmSupplyPageReqVO extends PageParam {
    private String name;
    private String cat;
    /** 批次号，模糊匹配 */
    private String batchNo;
    private Integer status;
}
