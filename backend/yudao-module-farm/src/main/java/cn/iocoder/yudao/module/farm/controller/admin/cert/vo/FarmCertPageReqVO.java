package cn.iocoder.yudao.module.farm.controller.admin.cert.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** 资质证照分页查询（第二期 B） */
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmCertPageReqVO extends PageParam {
    private String name;
    /** 必备 / 建议 / 按需 */
    private String type;
}
