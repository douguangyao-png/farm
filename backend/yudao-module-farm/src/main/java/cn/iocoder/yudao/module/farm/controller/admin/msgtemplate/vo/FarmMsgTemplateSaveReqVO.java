package cn.iocoder.yudao.module.farm.controller.admin.msgtemplate.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** 新建 / 编辑 / 启停 / 测试发送（第二期 B） */
@Data
public class FarmMsgTemplateSaveReqVO {
    private Long id;
    private String name;
    private String trigger;
    private List<String> channels;
    private String content;
    private Integer status;
}
