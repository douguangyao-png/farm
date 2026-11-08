package cn.iocoder.yudao.module.farm.controller.admin.shipbatch;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.shipbatch.vo.FarmShipBatchCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipbatch.vo.FarmShipBatchIdReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipbatch.vo.FarmShipBatchPageReqVO;
import cn.iocoder.yudao.module.farm.service.FarmShipBatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 配送批次：/admin-api/farm/ship-batch/**（第一期补齐） */
@Tag(name = "管理后台 - 认养农场 - 配送批次")
@RestController
@RequestMapping("/farm/ship-batch")
public class FarmShipBatchController {

    @Autowired
    private FarmShipBatchService service;

    @GetMapping("/page")
    public CommonResult<PageResult<Map<String, Object>>> page(FarmShipBatchPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmShipBatchCreateReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/ship")
    public CommonResult<Boolean> ship(@RequestBody FarmShipBatchIdReqVO req) {
        service.ship(req.getId());
        return success(true);
    }

    @PutMapping("/finish")
    public CommonResult<Boolean> finish(@RequestBody FarmShipBatchIdReqVO req) {
        service.finish(req.getId());
        return success(true);
    }
}
