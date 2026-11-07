package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** farm_refund 退款单（建表 SQL 见 docs/plans/第二期-登记/A.md）。金额单位：分 */
@TableName(value = "farm_refund", autoResultMap = true)
@KeySequence("farm_refund_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmRefundDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String no;
    private Long orderId;
    private Long memberId;
    /** 实付 */
    private Long amount;
    /** 已发生农事投入 */
    private Long investDeduct;
    /** 已交付农产品折算 */
    private Long deliverDeduct;
    /** 扣减合计 */
    private Long deduct;
    /** 应退 */
    private Long refundAmount;
    private String reason;
    /** 0 待审核 / 10 已通过 / 20 已驳回 */
    private Integer status;
    private String auditRemark;
    /** 申请前的订单状态，驳回时恢复 */
    private Integer prevOrderStatus;
    private LocalDateTime auditTime;
}
