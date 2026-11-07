package cn.iocoder.yudao.module.farm.controller.admin.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对应 mock orderView */
@Data
public class FarmOrderRespVO {
    private Long id;
    private String no;
    private Long memberId;
    private String memberName;
    private String memberMobile;
    private Long productId;
    private String productTitle;
    private String productCover;
    private String careMode;
    private String careModeName;
    /** 分 */
    private Long amount;
    private Integer status;
    private Long assetId;
    private String assetCode;
    private Long adoptionId;
    private String contractVersion;
    private Boolean contractAgreed;
    private String receiverName;
    private String receiverMobile;
    private String address;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    /** 演示口径：固定「微信支付」 */
    private String payMethod;
    /** 约定交付（斤），不托管/未认养为 null */
    private BigDecimal promisedJin;
    /** 已收（斤） */
    private BigDecimal harvestedJin;
    /** 履约进度 0-100 整数 = 已收 / 约定交付 */
    private Integer progress;
}
