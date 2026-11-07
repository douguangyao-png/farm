package cn.iocoder.yudao.module.farm.controller.admin.shipment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentSignReqVO;
import cn.iocoder.yudao.module.farm.service.FarmShipmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.9 配送：/admin-api/farm/shipment/** */
@Tag(name = "管理后台 - 认养农场 - 配送")
@RestController
@RequestMapping("/farm/shipment")
public class FarmShipmentController {

    @Resource
    private FarmShipmentService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmShipmentRespVO>> page(FarmShipmentPageReqVO req) {
        return success(service.page(req));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmShipmentCreateReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/sign")
    public CommonResult<Boolean> sign(@RequestBody FarmShipmentSignReqVO req) {
        service.sign(req.getId());
        return success(true);
    }
}
