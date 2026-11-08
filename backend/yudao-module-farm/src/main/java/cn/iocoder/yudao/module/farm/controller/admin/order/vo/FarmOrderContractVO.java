package cn.iocoder.yudao.module.farm.controller.admin.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 对应 mock contractOf */
@Data
public class FarmOrderContractVO {
    private String no;
    private String version;
    private String title;
    private Boolean agreed;
    /** 手写签名时间；没签名的老数据沿用支付时间 */
    private LocalDateTime signedAt;
    /** 是否已手写签名（签名图片本身只在 C 端合同接口返回） */
    private Boolean hasSignature;
}
