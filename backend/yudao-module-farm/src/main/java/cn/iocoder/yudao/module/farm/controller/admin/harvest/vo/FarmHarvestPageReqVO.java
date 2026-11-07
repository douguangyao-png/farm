package cn.iocoder.yudao.module.farm.controller.admin.harvest.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmHarvestPageReqVO extends PageParam {
    /** 资产编号，模糊匹配 */
    private String assetCode;
    /** true 只看已发货，false 只看未发货，不传不过滤 */
    private Boolean shipped;
}
