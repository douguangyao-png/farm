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
import java.util.List;
import java.util.Map;

/** farm_fund_notice（第二期 B；建表 SQL 见 docs/plans/第二期-登记/B.md） */
@TableName(value = "farm_fund_notice", autoResultMap = true)
@KeySequence("farm_fund_notice_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmFundNoticeDO extends TenantBaseDO {
    @TableId
    private Long id;
    /** 公示字段开关：{flowChart:true,...}，每个租户一行 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> fields;
}
