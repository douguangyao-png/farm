package cn.iocoder.yudao.module.farm.controller.admin.asset.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 对应 mock 的 assetView */
@Data
public class FarmAssetRespVO {
    private Long id;
    private Long tenantId;
    private Long baseId;
    private String assetType;
    private String code;
    private String name;
    private Map<String, Object> specs;
    private Integer status;
    private String cameraName;
    private String sensorName;
    private LocalDateTime createTime;
    private String baseName;
    /** 当前认养中的认养记录（status=10），没有则 null */
    private Long adoptionId;
    private String memberName;
    private String careMode;
}
