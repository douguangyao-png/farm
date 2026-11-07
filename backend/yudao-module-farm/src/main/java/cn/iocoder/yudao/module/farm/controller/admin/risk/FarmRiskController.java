package cn.iocoder.yudao.module.farm.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.service.FarmRiskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.1 风险自检：/admin-api/farm/risk/self-check */
@Tag(name = "管理后台 - 认养农场 - 风险自检")
@RestController
@RequestMapping("/farm/risk")
public class FarmRiskController {

    @Autowired
    private FarmRiskService service;

    @GetMapping("/self-check")
    public CommonResult<Map<String, Object>> selfCheck() {
        return success(service.selfCheck());
    }
}
