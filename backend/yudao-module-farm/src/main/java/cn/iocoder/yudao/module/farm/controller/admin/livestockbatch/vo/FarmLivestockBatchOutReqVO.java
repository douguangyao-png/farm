package cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo;

import lombok.Data;

@Data
public class FarmLivestockBatchOutReqVO {
    private Long id;
    /** 本次出栏数量 */
    private Integer count;
}
