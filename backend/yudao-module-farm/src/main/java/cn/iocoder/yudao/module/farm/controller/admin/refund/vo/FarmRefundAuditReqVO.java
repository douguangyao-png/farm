package cn.iocoder.yudao.module.farm.controller.admin.refund.vo;

import lombok.Data;

@Data
public class FarmRefundAuditReqVO {
    private Long id;
    /** true 通过并退款 / false 驳回 */
    private Boolean approve;
    /** 审核意见；驳回必填，最长 200 字 */
    private String remark;
}
