package cn.iocoder.yudao.module.farm.controller.admin.asset.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmAssetPageReqVO extends PageParam {
    private Long baseId;
    private String assetType;
    private Integer status;
    private String code;
}
