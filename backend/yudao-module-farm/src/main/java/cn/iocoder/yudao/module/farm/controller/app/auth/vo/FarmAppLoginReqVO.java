package cn.iocoder.yudao.module.farm.controller.app.auth.vo;

import lombok.Data;

/** 手机号 + 密码登录（mock：app.ts 22 行 POST /member/auth/login） */
@Data
public class FarmAppLoginReqVO {
    private String mobile;
    private String password;
}
