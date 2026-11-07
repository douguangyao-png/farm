package cn.iocoder.yudao.module.farm.controller.admin.steward.vo;

import lombok.Data;

import java.util.Map;

@Data
public class FarmStewardCheckinRespVO {
    private Long recordId;
    /** { exifTime, gpsFence, duplicate } */
    private Map<String, Object> checks;
    /** { airTemp, airHumidity, soilMoisture } */
    private Map<String, Object> env;
}
