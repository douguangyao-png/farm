package cn.iocoder.yudao.module.farm.controller.admin.base.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FarmBaseRespVO {
    private Long id;
    private String name;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private BigDecimal areaMu;
    private String certNo;
    private String cropSummary;
    private Integer status;
    private String cover;
    private LocalDateTime createTime;
    /** 基地下资产数 */
    private Long assetCount;
    /** 绑定到该基地的管家数 */
    private Long stewardCount;
}
