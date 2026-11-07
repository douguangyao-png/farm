package cn.iocoder.yudao.module.farm.controller.app.order.vo;

import lombok.Data;

/** 只带 { id } 的请求体：支付、自管任务完成/委托共用 */
@Data
public class FarmAppIdReqVO {
    private Long id;
}
