package cn.iocoder.yudao.module.farm.controller.admin.serviceorder;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderRespVO;
import cn.iocoder.yudao.module.farm.service.FarmServiceOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.7 代管工单：/admin-api/farm/service-order/** */
@Tag(name = "管理后台 - 认养农场 - 代管工单")
@RestController
@RequestMapping("/farm/service-order")
public class FarmServiceOrderController {

    @Autowired
    private FarmServiceOrderService service;

    @GetMapping("/page")
    public CommonResult<FarmFulfillPage<FarmServiceOrderRespVO>> page(FarmServiceOrderPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<FarmServiceOrderRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }
}
