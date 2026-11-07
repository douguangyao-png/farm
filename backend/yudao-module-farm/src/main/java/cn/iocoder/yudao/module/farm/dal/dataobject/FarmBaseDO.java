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

/** farm_base（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_base", autoResultMap = true)
@KeySequence("farm_base_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmBaseDO extends TenantBaseDO {
    @TableId
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
}
