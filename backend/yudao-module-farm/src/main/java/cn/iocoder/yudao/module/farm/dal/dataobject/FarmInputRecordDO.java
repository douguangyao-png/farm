package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** farm_input_record（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_input_record", autoResultMap = true)
@KeySequence("farm_input_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmInputRecordDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long assetId;
    private String source;
    private String name;
    private String batchNo;
    private LocalDateTime usedAt;
    private String dosage;
    private String operator;
}
