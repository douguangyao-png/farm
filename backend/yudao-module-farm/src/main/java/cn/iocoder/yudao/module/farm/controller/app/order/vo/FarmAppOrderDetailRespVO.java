package cn.iocoder.yudao.module.farm.controller.app.order.vo;

import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderContractVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderRespVO;
import lombok.Data;

/** C 端订单详情（mock：farm-app.ts 135–140 行）：订单视图 + 合同 */
@Data
public class FarmAppOrderDetailRespVO extends FarmOrderRespVO {
    private FarmOrderContractVO contract;
}
