package cn.iocoder.yudao.module.farm.controller.admin.asset.vo;

import lombok.Data;

import java.util.Map;

/** 资产新增 / 修改。必填校验放在 Service 里，以便返回契约要求的 400 + 中文提示 */
@Data
public class FarmAssetSaveReqVO {
    private Long id;
    private Long baseId;
    private String assetType;
    private String code;
    private String name;
    private Map<String, Object> specs;
    private Integer status;
    private String cameraName;
    private String sensorName;
}
