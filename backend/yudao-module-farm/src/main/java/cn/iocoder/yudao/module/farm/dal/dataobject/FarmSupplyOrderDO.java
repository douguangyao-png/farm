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

/** farm_supply_order（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_supply_order", autoResultMap = true)
@KeySequence("farm_supply_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmSupplyOrderDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String no;
    private Long memberId;
    private Long adoptionId;
    private Long assetId;
    private Long spuId;
    private Integer qty;
    private Long amount;
    private String deliverWay;
    private String batchNo;
    private Integer traceState;
    private LocalDateTime usedAt;
    private String dosage;
    private String receiverName;
    private String receiverMobile;
    private String address;
}
