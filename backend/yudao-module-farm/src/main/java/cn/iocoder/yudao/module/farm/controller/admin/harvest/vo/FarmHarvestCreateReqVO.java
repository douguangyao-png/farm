package cn.iocoder.yudao.module.farm.controller.admin.harvest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FarmHarvestCreateReqVO {
    private Long adoptionId;
    private String crop;
    private BigDecimal weightJin;
    /** 采收日期，毫秒时间戳；不传按今天 */
    private Long harvestDate;
    private List<String> photos;
    private String remark;
}
