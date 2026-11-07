package cn.iocoder.yudao.module.farm.controller.app.adoption.vo;

import lombok.Data;

/** 摄像头（mock：farm-app.ts 189–194 行）：演示环境是占位画面 */
@Data
public class FarmAppCameraRespVO {
    private String name;
    private Boolean online;
    private String poster;
    private String tip;
}
