package cn.iocoder.yudao.module.farm.controller.admin.supply.vo;

import lombok.Data;

import java.util.List;

/** 对应 mock 的 supplyView（即 Supply 本身） */
@Data
public class FarmSupplyRespVO {
    private Long id;
    private Long tenantId;
    private String name;
    private String cat;
    private String cover;
    private Long price;
    private Long cost;
    private Integer stock;
    private String unit;
    private String spec;
    private List<String> fitModes;
    private String batchNo;
    private Boolean retailAllowed;
    private Boolean needWorkorder;
    private Integer status;
    private Integer sold;
    private String description;
    /** 编号 SP-(100+id) */
    private String code;
    /** 毛利率（%，整数）= (售价-成本)/售价 */
    private Integer grossMargin;
    /** 已下架 / 需工单 / 低库存 / 正常 */
    private String stateName;
}
