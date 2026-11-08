package cn.iocoder.yudao.module.farm.controller.app.contract.vo;

import lombok.Data;

@Data
public class FarmAppContractSignReqVO {
    /** 合同编号 = 订单编号，与 orderId 二选一 */
    private Long contractId;
    private Long orderId;
    /** PNG 的 base64 或 data:image/png;base64,... ，解码后不超过 200KB */
    private String signature;
}
