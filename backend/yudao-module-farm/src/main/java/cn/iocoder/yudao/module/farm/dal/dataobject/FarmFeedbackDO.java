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

/** farm_feedback（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_feedback", autoResultMap = true)
@KeySequence("farm_feedback_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmFeedbackDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String no;
    private String type;
    private String content;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> screenshots;
    private String pageTitle;
    private String pagePath;
    private String userAgent;
    private Long creatorId;
    private String creatorName;
    private Integer status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> replies;
}
