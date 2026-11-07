package cn.iocoder.yudao.module.farm.controller.admin.platform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.platform.vo.FarmRiskAlertHandleReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.platform.vo.FarmRiskAlertPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.platform.vo.FarmRiskAlertRespVO;
import cn.iocoder.yudao.module.farm.service.FarmPlatformService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 1.2 平台端：风控预警、总览（仅平台，经营者调用返回 403） */
@Tag(name = "管理后台 - 认养农场 - 平台")
@RestController
@RequestMapping("/farm")
public class FarmPlatformController {

    @Resource
    private FarmPlatformService service;

    @GetMapping("/risk-alert/page")
    public CommonResult<PageResult<FarmRiskAlertRespVO>> alertPage(FarmRiskAlertPageReqVO req) {
        return success(service.alertPage(req));
    }

    @PutMapping("/risk-alert/handle")
    public CommonResult<Boolean> alertHandle(@RequestBody FarmRiskAlertHandleReqVO req) {
        service.alertHandle(req);
        return success(true);
    }

    @GetMapping("/platform/overview")
    public CommonResult<Map<String, Object>> overview() {
        return success(service.overview());
    }
}
