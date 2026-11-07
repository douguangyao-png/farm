package cn.iocoder.yudao.module.farm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 认养农场错误码，使用 1-030-000-000 段（与 docs/api/farm-接口契约.md 0.2 一致）。
 * 普通的参数错误按契约用 400；{} 是占位符，由调用处 exception(BAD_REQUEST, "提示语") 传入。
 */
public interface ErrorCodeConstants {

    ErrorCode BAD_REQUEST = new ErrorCode(400, "{}");
    ErrorCode NOT_FOUND = new ErrorCode(404, "{}不存在");

    ErrorCode PRODUCT_BANNED_WORDS = new ErrorCode(1_030_000_000, "{}");
    ErrorCode ORDER_CONTRACT_NOT_AGREED = new ErrorCode(1_030_000_001, "请先阅读并勾选认养合同");
    ErrorCode ORDER_STATUS_CHANGED = new ErrorCode(1_030_000_002, "订单状态已变化，请刷新");
    ErrorCode ORDER_NO_FREE_ASSET = new ErrorCode(1_030_000_003, "这个商品暂时没有空闲的地块");
    ErrorCode SUPPLY_NEED_ADOPTION = new ErrorCode(1_030_000_004, "请选择要使用这份农资的认养地块");
    ErrorCode SUPPLY_NOT_RETAIL = new ErrorCode(1_030_000_005, "该农资不能零售，只能由农场代施");
    ErrorCode TASK_STATUS_CHANGED = new ErrorCode(1_030_000_006, "任务状态已变化，请刷新");
    ErrorCode TASK_NOT_MINE = new ErrorCode(1_030_000_007, "只能处理分配给自己的任务");

    ErrorCode FORBIDDEN = new ErrorCode(403, "{}");
}
