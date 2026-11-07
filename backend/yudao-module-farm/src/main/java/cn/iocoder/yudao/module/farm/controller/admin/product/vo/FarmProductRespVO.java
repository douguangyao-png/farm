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
    /** 编号 P001（P + id 补零到 3 位） */
    private String no;
    /** 认养分类名：土地认养 / 家禽认养 / 果树认养 */
    private String categoryName;
    /** 总认养位 = 库存 + 已售 */
    private Integer totalSlots;
    /** 认养进度 0-100 = 已售 / 总认养位 */
    private Integer soldRate;
    /** 是否投保：任一已开放档位的保险说明含「含」字 */
    private Boolean insured;
    /** 预计产出：优先全托管档、否则第一个已开放档的产出说明 */
    private String expectedOutput;
}
