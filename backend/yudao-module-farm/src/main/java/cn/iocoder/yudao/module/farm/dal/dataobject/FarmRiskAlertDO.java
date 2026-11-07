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

/** farm_risk_alert（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_risk_alert", autoResultMap = true)
@KeySequence("farm_risk_alert_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmRiskAlertDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String type;
    private Integer level;
    private String title;
    private String content;
    private String source;
    private Integer status;
    private String handleRemark;
    private LocalDateTime handleTime;
}
