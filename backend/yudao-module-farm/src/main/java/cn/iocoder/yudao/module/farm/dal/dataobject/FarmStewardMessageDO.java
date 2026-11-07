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

/** farm_steward_message（建表 SQL 见 docs/plans/第二期-登记/管家端.md；mock：farm-steward-extra.ts） */
@TableName(value = "farm_steward_message", autoResultMap = true)
@KeySequence("farm_steward_message_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmStewardMessageDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long stewardId;
    private Long memberId;
    private Long assetId;
    /** steward / member */
    private String sender;
    private String content;
}
