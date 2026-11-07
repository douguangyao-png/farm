package cn.iocoder.yudao.module.farm.controller.admin.device.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** IoT 设备分页查询（第二期 B） */
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmDevicePageReqVO extends PageParam {
    private String code;
    /** camera / sensor */
    private String kind;
    /** online / offline */
    private String status;
}
