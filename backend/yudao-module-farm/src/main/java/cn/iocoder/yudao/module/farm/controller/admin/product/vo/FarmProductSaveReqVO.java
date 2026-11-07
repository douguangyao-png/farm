package cn.iocoder.yudao.module.farm.controller.admin.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 认养商品新增 / 修改。必填校验放在 Service 里，以便返回契约要求的 400 + 中文提示 */
@Data
public class FarmProductSaveReqVO {
    private Long id;
    private Long baseId;
    private String assetType;
    private String title;
    private String cover;
    private String description;
    private Integer cycleDays;
    private BigDecimal areaM2;
    private Integer stock;
    private Integer status;
    /** 三档托管配置：mode / enabled / price(分) / slogan / outputPromise / contractVersion / weeklyTodo / insurance / duty */
    private List<Map<String, Object>> careModes;
}
