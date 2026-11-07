package cn.iocoder.yudao.module.farm.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.farm.service.FarmDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.1 工作台：/admin-api/farm/dashboard/summary */
@Tag(name = "管理后台 - 认养农场 - 工作台")
@RestController
@RequestMapping("/farm/dashboard")
public class FarmDashboardController {

    @Autowired
    private FarmDashboardService service;

    @GetMapping("/summary")
    public CommonResult<Map<String, Object>> summary() {
        return success(service.summary(TenantContextHolder.getRequiredTenantId()));
    }
}
