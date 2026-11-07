package cn.iocoder.yudao.module.farm.controller.admin.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 对应 mock 的 productView */
@Data
public class FarmProductRespVO {
    private Long id;
    private Long tenantId;
    private Long baseId;
    private String assetType;
    private String title;
    private String cover;
    private String description;
    private Integer cycleDays;
    private BigDecimal areaM2;
    private Long basePrice;
    private Integer stock;
    private Integer sold;
    private Integer status;
    private List<Map<String, Object>> careModes;
    private LocalDateTime createTime;
    private String baseName;
    /** 已开放档位里的最低价（分）；没有开放档位时为 null */
    private Long minPrice;
    private List<String> enabledModes;
}
