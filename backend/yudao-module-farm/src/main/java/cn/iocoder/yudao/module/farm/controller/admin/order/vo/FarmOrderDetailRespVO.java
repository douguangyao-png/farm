package cn.iocoder.yudao.module.farm.controller.admin.order.vo;

import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentRespVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

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
    /** 客户信息卡：{ name, mobile, orderCount, totalAmount(分) } */
    private Map<String, Object> customer;
    /** 专属管家：{ name, mobile(脱敏) }，没有认养关系时为 null */
    private Map<String, Object> steward;
    /** 客户标签（新客/多单客户/全托管用户/有退款记录 + 分层名），由客户分层派生，不落库 */
    private java.util.List<String> customerTags;
}
