package cn.iocoder.yudao.module.farm.controller.app.order.vo;

import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderContractVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderRespVO;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端订单视图（mock：farm-app.ts appOrderView）：订单视图 + 合同 + 认养摘要。
 * /app-api/farm/order/get 和 /order/list 共用；未支付的订单认养相关字段为 null / 空串 / 0。
 */
@Data
public class FarmAppOrderDetailRespVO extends FarmOrderRespVO {
    private FarmOrderContractVO contract;
    /** 认养编号，演示口径 A{年}-{认养 id 补 4 位}，未支付为 null */
    private String adoptionNo;
    /** 认养状态（10 认养中 / 20 已完成…），未支付为 null */
    private Integer adoptionStatus;
    /** 服务周期开始 / 结束，未支付为 null */
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** 该认养已有多少张配送单 */
    private Long shipmentCount;
    /** 地块名称、面积（㎡） */
    private String assetName;
    private Object areaM2;
    /** 专属管家姓名、手机号（脱敏），没有为空串 */
    private String stewardName;
    private String stewardMobile;
    /** 演示保单号 PICC-{年}-{订单 id 补 4 位}，未支付为 null */
    private String insuranceNo;
    /** 演示溯源码 TR{订单号去掉前两位}，未支付为 null */
    private String traceCode;
}
