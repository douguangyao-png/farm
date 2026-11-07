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

/** farm_asset（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_asset", autoResultMap = true)
@KeySequence("farm_asset_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmAssetDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long baseId;
    private String assetType;
    private String code;
    private String name;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> specs;
    private Integer status;
    private String cameraName;
    private String sensorName;
}
