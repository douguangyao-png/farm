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
import java.util.List;
import java.util.Map;

/** farm_shipment（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_shipment", autoResultMap = true)
@KeySequence("farm_shipment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmShipmentDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String no;
    private Long harvestId;
    private Long adoptionId;
    private Long memberId;
    private String receiverName;
    private String receiverMobile;
    private String address;
    private BigDecimal weightJin;
    private String carrier;
    private String logisticsNo;
    private Boolean coldChain;
    private Integer status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> tracks;
    /** 所属配送批次（farm_ship_batch.id），未入批为空 */
    private Long batchId;
}
