package cn.iocoder.yudao.module.farm.controller.admin.record.vo;

import lombok.Data;

@Data
public class FarmRecordAuditReqVO {
    private Long id;
    private Boolean pass;
    private String remark;
}
