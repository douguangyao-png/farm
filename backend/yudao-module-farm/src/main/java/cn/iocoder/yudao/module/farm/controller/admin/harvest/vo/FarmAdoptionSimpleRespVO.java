package cn.iocoder.yudao.module.farm.controller.admin.harvest.vo;

import lombok.Data;

/** 认养人下拉：{ id, label, assetCode, memberName, careMode } */
@Data
public class FarmAdoptionSimpleRespVO {
    private Long id;
    /** 资产编号 · 认养人 · 托管方式 */
    private String label;
    private String assetCode;
    private String memberName;
    private String careMode;
}
