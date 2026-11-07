package cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo;

import lombok.Data;

/** 登记施用。必填校验放在 Service 里，以便返回契约要求的 400 + 中文提示 */
@Data
public class FarmSupplyOrderTraceReqVO {
    private Long id;
    /** 施用时间，毫秒时间戳 */
    private Long usedAt;
    private String dosage;
}
