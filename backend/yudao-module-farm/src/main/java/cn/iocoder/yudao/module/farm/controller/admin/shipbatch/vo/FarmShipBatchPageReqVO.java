package cn.iocoder.yudao.module.farm.controller.admin.shipbatch.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmShipBatchPageReqVO extends PageParam {
    /** 0 待发 / 10 已发出 / 20 已完成，不传不过滤 */
    private Integer status;
    /** 批次号，模糊匹配 */
    private String no;
}
