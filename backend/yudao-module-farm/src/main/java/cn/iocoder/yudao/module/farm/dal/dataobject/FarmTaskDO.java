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

/** farm_task（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_task", autoResultMap = true)
@KeySequence("farm_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmTaskDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long adoptionId;
    private Long assetId;
    private String owner;
    private Long stewardId;
    private String title;
    private String description;
    private String source;
    private Long serviceOrderId;
    private LocalDateTime deadline;
    private Integer status;
    private Long entrustPrice;
    private LocalDateTime finishTime;
}
