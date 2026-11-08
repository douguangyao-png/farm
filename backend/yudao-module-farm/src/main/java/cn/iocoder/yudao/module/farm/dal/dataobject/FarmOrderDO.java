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
    /** 第一期补齐：来源渠道 mini/h5/scan/steward */
    private String channel;
    private String packageName;
    private String couponName;
    /** 分 */
    private Long couponAmount;
    private BigDecimal gpsLng;
    private BigDecimal gpsLat;
    private String gpsText;
    /** express 快递到家 / pickup 到基地自提 / farm_deliver 基地配送 */
    private String deliverWay;
    /** 合同手写签名（PNG dataURL），不进订单视图 */
    private String signature;
    private LocalDateTime signedAt;
}
