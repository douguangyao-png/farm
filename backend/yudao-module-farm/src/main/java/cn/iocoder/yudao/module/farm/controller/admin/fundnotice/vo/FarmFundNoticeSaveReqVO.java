package cn.iocoder.yudao.module.farm.controller.admin.fundnotice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** 保存资金公示开关（第二期 B）：fields = {flowChart:true,...} */
@Data
public class FarmFundNoticeSaveReqVO {
    private Map<String, Object> fields;
}
