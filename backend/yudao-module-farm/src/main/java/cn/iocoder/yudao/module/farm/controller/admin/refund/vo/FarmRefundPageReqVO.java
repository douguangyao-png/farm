package cn.iocoder.yudao.module.farm.controller.admin.refund.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmRefundPageReqVO extends PageParam {
    /** 退款单号，模糊 */
    private String no;
    /** 订单号，模糊 */
    private String orderNo;
    /** 认养人手机号，模糊 */
    private String memberMobile;
    /** 0 待审核 / 10 已通过 / 20 已驳回 */
    private Integer status;
}
