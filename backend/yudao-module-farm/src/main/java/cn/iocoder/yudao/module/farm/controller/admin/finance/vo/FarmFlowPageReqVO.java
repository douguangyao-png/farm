package cn.iocoder.yudao.module.farm.controller.admin.finance.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** 交易流水分页查询（第二期 B） */
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmFlowPageReqVO extends PageParam {
    /** 交易号，模糊 */
    private String tradeNo;
    /** 认养人昵称，模糊 */
    private String memberName;
    /** adopt / supply / service */
    private String type;
    /** settled / settling */
    private String settleStatus;
    /** 毫秒时间戳，含边界 */
    private Long beginTime;
    private Long endTime;
}
