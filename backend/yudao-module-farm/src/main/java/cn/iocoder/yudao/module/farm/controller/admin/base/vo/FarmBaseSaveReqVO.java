package cn.iocoder.yudao.module.farm.controller.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 基地新增 / 修改。必填校验放在 Service 里，以便返回契约要求的 400 + 中文提示 */
@Data
public class FarmBaseSaveReqVO {
    private Long id;
    private String name;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private BigDecimal areaMu;
    private String certNo;
    private String cropSummary;
    private Integer status;
}
