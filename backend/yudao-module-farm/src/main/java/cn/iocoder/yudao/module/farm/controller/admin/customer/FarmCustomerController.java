package cn.iocoder.yudao.module.farm.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.customer.vo.FarmCustomerPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.customer.vo.FarmCustomerRespVO;
import cn.iocoder.yudao.module.farm.service.FarmCustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 客户管理：/admin-api/farm/customer/**（mock：routes/farm-people.ts） */
@Tag(name = "管理后台 - 认养农场 - 客户管理")
@RestController
@RequestMapping("/farm/customer")
public class FarmCustomerController {

    @Autowired
    private FarmCustomerService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmCustomerRespVO>> page(FarmCustomerPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/stats")
    public CommonResult<Map<String, Object>> stats() {
        return success(service.stats());
    }
}
