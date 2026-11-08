package cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo;

import lombok.Data;

@Data
public class FarmLivestockBatchSaveReqVO {
    /** 更新时必填 */
    private Long id;
    private Long baseId;
    private String species;
    private String breed;
    /** 入栏日期，毫秒时间戳；不传 = 今天 */
    private Long inDate;
    private Integer count;
    private String pen;
    private String remark;
}
