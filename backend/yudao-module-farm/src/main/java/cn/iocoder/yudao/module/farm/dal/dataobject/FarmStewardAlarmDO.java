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

/** farm_steward_alarm（建表 SQL 见 docs/plans/第二期-登记/管家端.md；mock：farm-steward-extra.ts） */
@TableName(value = "farm_steward_alarm", autoResultMap = true)
@KeySequence("farm_steward_alarm_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmStewardAlarmDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long stewardId;
    /** 设备异常 / 损耗登记 / 气象灾害 / 病虫害爆发 */
    private String type;
    /** 高 / 中 */
    private String level;
    private Long assetId;
    private String title;
    private String content;
    private Integer qty;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> photos;
    private Boolean insuranceReported;
    private Integer status;
}
