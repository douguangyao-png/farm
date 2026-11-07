package cn.iocoder.yudao.module.farm.controller.admin.harvest;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmAdoptionSimpleRespVO;
import cn.iocoder.yudao.module.farm.service.FarmHarvestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 录入收成时的认养人下拉：/admin-api/farm/adoption/simple-list（mock：farm-admin.ts 362–367 行） */
@Tag(name = "管理后台 - 认养农场 - 认养人下拉")
@RestController
@RequestMapping("/farm/adoption")
public class FarmAdoptionSimpleController {

    @Resource
    private FarmHarvestService service;

    @GetMapping("/simple-list")
    public CommonResult<List<FarmAdoptionSimpleRespVO>> simpleList() {
        return success(service.adoptionSimpleList());
    }
}
