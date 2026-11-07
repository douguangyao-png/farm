package cn.iocoder.yudao.module.farm.controller.admin.harvest;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestRespVO;
import cn.iocoder.yudao.module.farm.service.FarmHarvestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.9 收成：/admin-api/farm/harvest/** */
@Tag(name = "管理后台 - 认养农场 - 收成")
@RestController
@RequestMapping("/farm/harvest")
public class FarmHarvestController {

    @Resource
    private FarmHarvestService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmHarvestRespVO>> page(FarmHarvestPageReqVO req) {
        return success(service.page(req));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmHarvestCreateReqVO req) {
        return success(service.create(req));
    }
}
