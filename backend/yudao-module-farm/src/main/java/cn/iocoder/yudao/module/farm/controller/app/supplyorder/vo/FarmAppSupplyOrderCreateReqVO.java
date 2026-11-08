package cn.iocoder.yudao.module.farm.controller.app.supplyorder.vo;

import lombok.Data;

/** 农资下单（mock：farm-app.ts 239 行；契约 4 节 POST /farm/supply-order/create） */
@Data
public class FarmAppSupplyOrderCreateReqVO {
    private Long spuId;
    /** 数量，要求整数且 >= 1（用 Double 接，才能识别 1.5 这类非法值，与 mock 的 Number.isInteger 一致） */
    private Double qty;
    /** 使用这份农资的认养编号，必填 */
    private Long adoptionId;
    /** store 寄存基地、express 快递到家、pickup 基地自提、apply 农场代施；其它值按 store */
    private String deliverWay;
    /** deliverWay=express 时三项必填（手机号 11 位）；其它方式可空 */
    private String receiverName;
    private String receiverMobile;
    private String address;
}
