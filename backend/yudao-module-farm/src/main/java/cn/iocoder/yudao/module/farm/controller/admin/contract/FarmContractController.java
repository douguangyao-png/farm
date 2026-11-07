package cn.iocoder.yudao.module.farm.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.contract.vo.FarmContractPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.contract.vo.FarmContractRespVO;
import cn.iocoder.yudao.module.farm.service.FarmContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.5.1 合同管理：/admin-api/farm/contract/** */
@Tag(name = "管理后台 - 认养农场 - 合同管理")
@RestController
@RequestMapping("/farm/contract")
public class FarmContractController {

    @Autowired
    private FarmContractService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmContractRespVO>> page(FarmContractPageReqVO req) {
        return success(service.page(req));
    }
}
