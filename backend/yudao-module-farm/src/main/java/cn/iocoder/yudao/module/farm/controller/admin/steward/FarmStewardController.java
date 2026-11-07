package cn.iocoder.yudao.module.farm.controller.admin.steward;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardAcceptReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardAssetDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardCheckinReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardCheckinRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardHomeRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardTaskDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardTaskRespVO;
import cn.iocoder.yudao.module.farm.service.FarmStewardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 3 管家端：/admin-api/farm/steward/**（mock：farm-steward.ts） */
@Tag(name = "管理后台 - 认养农场 - 管家端")
@RestController
@RequestMapping("/farm/steward")
public class FarmStewardController {

    @Autowired
    private FarmStewardService service;

    @GetMapping("/home")
    public CommonResult<FarmStewardHomeRespVO> home() {
        return success(service.home());
    }

    @GetMapping("/task/list")
    public CommonResult<List<FarmStewardTaskRespVO>> taskList(@RequestParam(value = "status", required = false) Integer status) {
        return success(service.taskList(status));
    }

    @GetMapping("/task/get")
    public CommonResult<FarmStewardTaskDetailRespVO> taskGet(@RequestParam("id") Long id) {
        return success(service.taskGet(id));
    }

    @PostMapping("/task/checkin")
    public CommonResult<FarmStewardCheckinRespVO> checkin(@RequestBody FarmStewardCheckinReqVO req) {
        return success(service.checkin(req));
    }

    @GetMapping("/service-order/list")
    public CommonResult<List<FarmServiceOrderRespVO>> serviceOrderList(@RequestParam(value = "scope", required = false) String scope) {
        return success(service.serviceOrderList(scope));
    }

    @PutMapping("/service-order/accept")
    public CommonResult<Boolean> serviceOrderAccept(@RequestBody FarmStewardAcceptReqVO req) {
        service.serviceOrderAccept(req.getId());
        return success(true);
    }

    @GetMapping("/asset/get")
    public CommonResult<FarmStewardAssetDetailRespVO> assetGet(@RequestParam("id") Long id) {
        return success(service.assetGet(id));
    }
}
