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

/** farm_msg_template（第二期 B；建表 SQL 见 docs/plans/第二期-登记/B.md） */
@TableName(value = "farm_msg_template", autoResultMap = true)
@KeySequence("farm_msg_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmMsgTemplateDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String name;
    /** 触发时机（trigger 是 SQL 关键字，列名用 trigger_text） */
    private String triggerText;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> channels;
    private String content;
    /** 0 启用 / 1 停用 */
    private Integer status;
}
