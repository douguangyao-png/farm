package cn.iocoder.yudao.module.farm.controller.admin.supply.vo;

import lombok.Data;

import java.util.List;

/** 农资商品新增 / 修改。必填校验放在 Service 里，以便返回契约要求的 400 + 中文提示 */
@Data
public class FarmSupplySaveReqVO {
    private Long id;
    private String name;
    private String cat;
    private String cover;
    /** 分 */
    private Long price;
    /** 分 */
    private Long cost;
    private Integer stock;
    private String unit;
    private String spec;
    private List<String> fitModes;
    private String batchNo;
    private Boolean retailAllowed;
    private Boolean needWorkorder;
    private Integer status;
    private String description;
}
