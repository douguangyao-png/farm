package cn.iocoder.yudao.module.farm.controller.app.adoption.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 溯源（mock：farm-app.ts 165–183 行）：六段 stages + 投入品 inputs */
@Data
public class FarmAppTraceRespVO {

    /** [{ key, title, time(毫秒时间戳或 null), content }] */
    private List<Map<String, Object>> stages;
    private List<Input> inputs;
    /** 有自购农资还没登记施用 */
    private Boolean pendingTrace;

    /** 投入品记录：source = farm | member */
    @Data
    public static class Input {
        private Long id;
        private Long assetId;
        private String source;
        private String name;
        private String batchNo;
        private LocalDateTime usedAt;
        private String dosage;
        private String operator;
    }
}
