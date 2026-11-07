package cn.iocoder.yudao.module.farm.controller.app.auth.vo;

import lombok.Data;

/** 对应 mock GET /member/user/get：积分、经验、等级、分销在认养 Demo 里固定为空值 */
@Data
public class FarmAppMemberRespVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String mobile;
    private String address;
    private Integer point = 0;
    private Integer experience = 0;
    private Object level;
    private Boolean brokerageEnabled = false;
}
