package cn.iocoder.yudao.module.farm.controller.admin.customer.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmCustomerPageReqVO extends PageParam {
    /** 姓名或手机号，模糊 */
    private String keyword;
    private String city;
    /** 标签：新客 / 多单客户 / 全托管用户 / 有退款记录 */
    private String tag;
    /** core / growth / new / sleep / other */
    private String tier;
    /** 买过该商品 */
    private Long productId;
    /** N 天内有下单/支付动作 */
    private Integer activeDays;
    /** 累计消费区间（分） */
    private Long minAmount;
    private Long maxAmount;
    /** 传 1：只看 30 天内有认养到期的客户 */
    private Integer expiring;
}
