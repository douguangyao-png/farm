package cn.iocoder.yudao.module.farm.controller.admin.steward.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Map;

/** 任务 + asset + env（mock farm-steward.ts 44–50 行） */
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmStewardTaskDetailRespVO extends FarmStewardTaskRespVO {
    private AssetBrief asset;
    /** 当前环境快照 envView(a).current */
    private Map<String, Object> env;

    @Data
    public static class AssetBrief {
        private Long id;
        private String code;
        private String name;
        private String crop;
        private Object areaM2;
        private String cameraName;
    }
}
