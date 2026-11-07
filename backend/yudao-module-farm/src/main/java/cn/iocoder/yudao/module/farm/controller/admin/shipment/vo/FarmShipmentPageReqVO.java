package cn.iocoder.yudao.module.farm.controller.admin.shipment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmShipmentPageReqVO extends PageParam {
    private Integer status;
    /** 配送单号，模糊匹配 */
    private String no;
}
