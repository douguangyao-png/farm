package cn.iocoder.yudao.module.farm.controller.admin.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应 mock adoptionView */
@Data
public class FarmOrderAdoptionVO {
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
    private String productTitle;
    private String cover;
    private String assetCode;
    private String assetType;
    private String baseName;
    private String memberName;
    private String careModeName;
    /** 认养人自管、未完成的任务数 */
    private Long selfTodoCount;
}
