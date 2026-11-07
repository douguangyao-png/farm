package cn.iocoder.yudao.module.farm.controller.admin.refund.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款单详情：在列表字段上加认养摘要和申请时的已过周期比例 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmRefundDetailRespVO extends FarmRefundRespVO {
    private Adoption adoption;
    /** 申请时已过周期比例 0-1，三位小数 */
    private Double elapsedRate;

    @Data
    public static class Adoption {
        private Long id;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private BigDecimal promisedJin;
        private BigDecimal harvestedJin;
    }
}
