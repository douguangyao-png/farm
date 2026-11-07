package cn.iocoder.yudao.module.farm.controller.admin.stewardmanage.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FarmStewardManagePageReqVO extends PageParam {
    /** 姓名或手机号，模糊 */
    private String keyword;
    private Long baseId;
}
