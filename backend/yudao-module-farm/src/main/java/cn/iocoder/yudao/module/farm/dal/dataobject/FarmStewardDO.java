package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;


/** farm_steward（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_steward", autoResultMap = true)
@KeySequence("farm_steward_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmStewardDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long userId;
    private Long baseId;
}
