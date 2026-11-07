package cn.iocoder.yudao.module.farm.controller.admin.record.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmRecordPageReqVO extends PageParam {
    private Integer status;
    /** 资产编号，模糊匹配 */
    private String assetCode;
    private Long stewardId;
}
