package cn.iocoder.yudao.module.farm.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderRespVO;
import cn.iocoder.yudao.module.farm.service.FarmOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.5 认养订单：/admin-api/farm/order/** */
@Tag(name = "管理后台 - 认养农场 - 认养订单")
@RestController
@RequestMapping("/farm/order")
public class FarmOrderController {

    @Autowired
    private FarmOrderService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmOrderRespVO>> page(FarmOrderPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<FarmOrderDetailRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }
}
