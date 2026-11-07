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
import java.time.LocalDateTime;

/** farm_adoption（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_adoption", autoResultMap = true)
@KeySequence("farm_adoption_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmAdoptionDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long orderId;
    private Long memberId;
    private Long productId;
    private Long assetId;
    private String careMode;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
    private BigDecimal promisedJin;
    private BigDecimal harvestedJin;
    private Long stewardId;
}
