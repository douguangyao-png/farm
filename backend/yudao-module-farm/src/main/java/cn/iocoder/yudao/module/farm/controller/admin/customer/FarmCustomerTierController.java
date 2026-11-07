package cn.iocoder.yudao.module.farm.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.service.FarmCustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 客户分层：/admin-api/farm/customer-tier/**（mock：routes/farm-people.ts） */
@Tag(name = "管理后台 - 认养农场 - 客户分层")
@RestController
@RequestMapping("/farm/customer-tier")
public class FarmCustomerTierController {

    @Autowired
    private FarmCustomerService service;

    @GetMapping("/summary")
    public CommonResult<Map<String, Object>> summary() {
        return success(service.tierSummary());
    }
}
