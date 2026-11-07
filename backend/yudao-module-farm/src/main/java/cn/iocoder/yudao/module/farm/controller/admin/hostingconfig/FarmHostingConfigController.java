package cn.iocoder.yudao.module.farm.controller.admin.hostingconfig;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.service.FarmHostingConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 托管模式配置 /farm/hosting-config/get（只读汇总；保存走 PUT /farm/product/update，第二期 B） */
@Tag(name = "管理后台 - 认养农场 - 托管模式配置")
@RestController
@RequestMapping("/farm/hosting-config")
public class FarmHostingConfigController {

    @Autowired
    private FarmHostingConfigService service;

    @GetMapping("/get")
    public CommonResult<Map<String, Object>> get(@RequestParam(value = "productId", required = false) Long productId) {
        return success(service.get(productId));
    }
}
