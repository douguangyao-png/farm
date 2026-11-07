package cn.iocoder.yudao.module.farm.controller.admin.customer.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 客户名单一行，由会员 + 订单派生（口径见 mock routes/farm-people.ts 顶部注释）。金额单位：分 */
@Data
public class FarmCustomerRespVO {
    private Long id;
    private String name;
    /** 已脱敏 */
    private String mobile;
    private String city;
    /** V1-V4，按累计消费的演示区间 */
    private String level;
    private Integer adoptCount;
    private Long totalAmount;
    private List<String> tags;
    private LocalDateTime lastActive;
    private String tier;
    private String tierName;
    private String renewIntent;
    private Integer activeAdoptionCount;
    private Integer expiringCount;
}
