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

/** farm_device（第二期 B；建表 SQL 见 docs/plans/第二期-登记/B.md） */
@TableName(value = "farm_device", autoResultMap = true)
@KeySequence("farm_device_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmDeviceDO extends TenantBaseDO {
    @TableId
    private Long id;
    /** 设备编号：手动添加的 CAM-X01 / SEN-X01；覆盖行与派生设备编号一致（CAM-01） */
    private String code;
    /** 1 = 手动添加的设备，0 = 对派生设备的覆盖（改位置/固件、派单） */
    private Integer custom;
    private String kind;
    private String typeName;
    private String name;
    private String position;
    private String bindText;
    private String firmware;
    private Integer dispatched;
}
