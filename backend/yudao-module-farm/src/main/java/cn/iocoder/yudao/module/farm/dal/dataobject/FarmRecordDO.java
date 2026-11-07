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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** farm_record（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_record", autoResultMap = true)
@KeySequence("farm_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmRecordDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long adoptionId;
    private Long assetId;
    private Long stewardId;
    private Long taskId;
    private String kind;
    private String title;
    private String content;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> photos;
    private BigDecimal lng;
    private BigDecimal lat;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> env;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> checks;
    private Integer status;
    private String auditRemark;
    private LocalDateTime auditTime;
}
