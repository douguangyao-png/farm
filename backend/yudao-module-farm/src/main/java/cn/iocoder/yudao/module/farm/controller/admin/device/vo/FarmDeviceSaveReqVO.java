package cn.iocoder.yudao.module.farm.controller.admin.device.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** 添加设备 / 配置 / 派单（第二期 B） */
@Data
public class FarmDeviceSaveReqVO {
    private String code;
    private String kind;
    private String typeName;
    private String name;
    private String position;
    private String bindText;
    private String firmware;
}
