package cn.iocoder.yudao.module.farm.controller.admin.supplyorder;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderTraceReqVO;
import cn.iocoder.yudao.module.farm.service.FarmSupplyOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.8 农资订单：/admin-api/farm/supply-order/** */
@Tag(name = "管理后台 - 认养农场 - 农资订单")
@RestController
@RequestMapping("/farm/supply-order")
public class FarmSupplyOrderController {

    @Autowired
    private FarmSupplyOrderService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmSupplyOrderRespVO>> page(FarmSupplyOrderPageReqVO req) {
        return success(service.page(req));
    }

    /** 登记施用：traceState→10，并写一条投入品记录 */
    @PutMapping("/trace")
    public CommonResult<Boolean> trace(@RequestBody FarmSupplyOrderTraceReqVO req) {
        service.trace(req);
        return success(true);
    }
}
