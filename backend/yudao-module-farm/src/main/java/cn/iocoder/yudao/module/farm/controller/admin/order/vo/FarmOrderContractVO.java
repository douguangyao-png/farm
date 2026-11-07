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
    private LocalDateTime signedAt;
}
