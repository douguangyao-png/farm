package cn.iocoder.yudao.module.farm.controller.app.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.app.contract.vo.FarmAppContractSignReqVO;
import cn.iocoder.yudao.module.farm.service.FarmAppContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** C 端合同（需登录）：/app-api/farm/contract/**（第一期补齐） */
@Tag(name = "用户 App - 认养农场 - 合同")
@RestController
@RequestMapping("/farm/contract")
public class FarmAppContractController {

    @Autowired
    private FarmAppContractService service;

    @GetMapping("/get")
    public CommonResult<Map<String, Object>> get(@RequestParam("orderId") Long orderId) {
        return success(service.get(orderId));
    }

    @PostMapping("/sign")
    public CommonResult<Map<String, Object>> sign(@RequestBody FarmAppContractSignReqVO req) {
        return success(service.sign(req));
    }
}
