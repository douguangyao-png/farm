package cn.iocoder.yudao.module.farm.controller.admin.msgtemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** 消息模板分页查询（第二期 B） */
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmMsgTemplatePageReqVO extends PageParam {
    private String name;
    /** 0 启用 / 1 停用 */
    private Integer status;
}
