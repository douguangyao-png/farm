package cn.iocoder.yudao.module.farm.controller.app.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** { userId, accessToken, refreshToken, expiresTime(毫秒时间戳) } */
@Data
public class FarmAppLoginRespVO {
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresTime;
}
