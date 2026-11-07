package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** farm_product（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_product", autoResultMap = true)
@KeySequence("farm_product_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmProductDO extends TenantBaseDO {
    @TableId
    private Long id;
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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> careModes;
}
