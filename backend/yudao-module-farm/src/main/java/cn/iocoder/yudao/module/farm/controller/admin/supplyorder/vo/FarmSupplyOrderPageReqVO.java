package cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmSupplyOrderPageReqVO extends PageParam {
    /** 0 待登记 / 10 已登记 */
    private Integer traceState;
    private String no;
    /** 订单号或认养人昵称，模糊匹配 */
    private String keyword;
    /** 绑定地块编号，模糊匹配 */
    private String assetCode;
    /** 订单状态（stateName 原文，精确匹配） */
    private String state;
}
