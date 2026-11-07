package cn.iocoder.yudao.module.farm.controller.admin.order.vo;

import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentRespVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 订单详情：订单字段 + contract / adoption / records / shipments（mock farm-admin.ts 251–262 行） */
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmOrderDetailRespVO extends FarmOrderRespVO {
    private FarmOrderContractVO contract;
    /** 没有认养关系（未支付）时为 null */
    private FarmOrderAdoptionVO adoption;
    /** 该认养最近 10 条已通过的农事记录 */
    private List<FarmRecordRespVO> records;
    private List<FarmShipmentRespVO> shipments;
}
