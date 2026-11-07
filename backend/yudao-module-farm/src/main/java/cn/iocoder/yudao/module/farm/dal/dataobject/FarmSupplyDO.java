package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** farm_supply（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_supply", autoResultMap = true)
@KeySequence("farm_supply_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmSupplyDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String name;
    private String cat;
    private String cover;
    private Long price;
    private Long cost;
    private Integer stock;
    private String unit;
    private String spec;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fitModes;
    private String batchNo;
    private Boolean retailAllowed;
    private Boolean needWorkorder;
    private Integer status;
    private Integer sold;
    private String description;
}
