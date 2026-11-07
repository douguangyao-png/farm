package cn.iocoder.yudao.module.farm.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmOrderPageReqVO extends PageParam {
    /** 订单号，模糊匹配 */
    private String no;
    /** 认养人手机号，模糊匹配 */
    private String memberMobile;
    /** full / semi / self */
    private String careMode;
    private Integer status;
    private Long productId;
    /** 下单时间起（毫秒时间戳，含） */
    private Long createTimeStart;
    /** 下单时间止（毫秒时间戳，含） */
    private Long createTimeEnd;
}
