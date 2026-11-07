package cn.iocoder.yudao.module.farm.controller.admin.steward.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FarmStewardCheckinReqVO {
    private Long taskId;
    private String content;
    private List<String> photos;
    private BigDecimal lng;
    private BigDecimal lat;
}
