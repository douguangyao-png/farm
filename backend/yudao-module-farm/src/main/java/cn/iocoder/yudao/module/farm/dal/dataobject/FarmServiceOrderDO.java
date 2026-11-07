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

/** farm_service_order（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_service_order", autoResultMap = true)
@KeySequence("farm_service_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmServiceOrderDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String no;
    private Long fromTaskId;
    private String taskTitle;
    private Long adoptionId;
    private Long assetId;
    private Long memberId;
    private Long stewardId;
    private String serviceType;
    private Long price;
    private Integer status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> split;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> proofPhotos;
    private String remark;
    private LocalDateTime acceptTime;
    private LocalDateTime finishTime;
}
