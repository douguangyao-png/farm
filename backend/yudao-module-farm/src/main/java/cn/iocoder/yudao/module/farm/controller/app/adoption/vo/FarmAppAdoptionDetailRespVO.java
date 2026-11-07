package cn.iocoder.yudao.module.farm.controller.app.adoption.vo;

import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderAdoptionVO;
import lombok.Data;

import java.util.Map;

/** C 端认养详情（mock：views.ts adoptionDetail）：认养视图 + 资产摘要 + 管家（手机号脱敏）+ 本档托管配置 */
@Data
public class FarmAppAdoptionDetailRespVO extends FarmOrderAdoptionVO {

    /** { id, code, name, crop, areaM2, cameraName, assetType } */
    private Map<String, Object> asset;
    /** { name, mobile(脱敏) }，没有管家时为 null */
    private Map<String, Object> steward;
    /** 商品里该托管档的配置（标语、责任、产出说明、合同版本），找不到为 null */
    private Map<String, Object> careModeInfo;
}
