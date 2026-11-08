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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** farm_harvest（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_harvest", autoResultMap = true)
@KeySequence("farm_harvest_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmHarvestDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long adoptionId;
    private Long assetId;
    private String crop;
    private BigDecimal weightJin;
    private LocalDate harvestDate;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> photos;
    private String remark;
    private Long shipmentId;
    /** 环境快照 JSON：{temp,humidity,soilMoisture,light,capturedAt,source}；老数据为空，视图里按采收日补 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> envSnapshot;
}
