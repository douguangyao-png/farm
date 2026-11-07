package cn.iocoder.yudao.module.farm.controller.admin.contract.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmContractPageReqVO extends PageParam {
    /** 合同编号（HT 开头）或订单号，模糊匹配 */
    private String no;
    /** 认养人手机号，模糊匹配 */
    private String memberMobile;
    /** 10 已签署 / 0 待签署 / 20 已解除 */
    private Integer status;
}
