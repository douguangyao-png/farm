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

/** farm_order（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_order", autoResultMap = true)
@KeySequence("farm_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmOrderDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String no;
    private Long memberId;
    private Long productId;
    private String careMode;
    private Long amount;
    private Integer status;
    private Long assetId;
    private Long adoptionId;
    private String contractVersion;
    private Boolean contractAgreed;
    private String receiverName;
    private String receiverMobile;
    private String address;
    private LocalDateTime payTime;
}
