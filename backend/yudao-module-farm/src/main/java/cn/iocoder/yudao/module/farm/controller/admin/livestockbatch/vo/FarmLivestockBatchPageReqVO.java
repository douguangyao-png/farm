package cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmLivestockBatchPageReqVO extends PageParam {
    /** 种类，模糊匹配 */
    private String species;
    /** 0 在栏 / 10 出栏中 / 20 已出栏 */
    private Integer status;
    private Long baseId;
}
