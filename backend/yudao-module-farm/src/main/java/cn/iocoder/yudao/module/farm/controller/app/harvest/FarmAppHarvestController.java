package cn.iocoder.yudao.module.farm.controller.app.harvest;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestRespVO;
import cn.iocoder.yudao.module.farm.service.FarmAppHarvestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** C 端收成（需登录）：/app-api/farm/harvest/**（第一期补齐，含 envSnapshot） */
@Tag(name = "用户 App - 认养农场 - 收成")
@RestController
@RequestMapping("/farm/harvest")
public class FarmAppHarvestController {

    @Autowired
    private FarmAppHarvestService service;

    @GetMapping("/list")
    public CommonResult<List<FarmHarvestRespVO>> list(@RequestParam(value = "adoptionId", required = false) Long adoptionId) {
        return success(service.list(adoptionId));
    }

    @GetMapping("/get")
    public CommonResult<FarmHarvestRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }
}
