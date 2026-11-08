package cn.iocoder.yudao.module.farm.controller.admin.contract.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 合同台账一行，由订单派生（对应 mock farm-contract.ts） */
@Data
public class FarmContractRespVO {
    /** 同订单 id */
    private Long id;
    /** 合同编号：HT + 订单号去掉前两位 */
    private String no;
    private Long orderId;
    private String orderNo;
    private String memberName;
    private String memberMobile;
    private String productTitle;
    private String careMode;
    private String careModeName;
    private String version;
    private String title;
    /** 10 已签署 / 0 待签署 / 20 已解除（订单已取消） */
    private Integer status;
    /** 演示口径：固定「电子签名」 */
    private String signMethod;
    /** 演示口径：已签署即 true */
    private Boolean caAuth;
    private Boolean chainProof;
    /** 手写签名过 = 签名时间；否则已签署 = 支付时间，其余 null */
    private LocalDateTime signedAt;
    private LocalDateTime createTime;
    /** 是否已手写签名 */
    private Boolean hasSignature;
}
