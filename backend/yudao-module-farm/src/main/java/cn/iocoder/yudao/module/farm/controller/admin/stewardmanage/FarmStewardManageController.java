package cn.iocoder.yudao.module.farm.controller.admin.stewardmanage;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.stewardmanage.vo.FarmStewardManagePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.stewardmanage.vo.FarmStewardManageRespVO;
import cn.iocoder.yudao.module.farm.service.FarmStewardManageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 管家管理：/admin-api/farm/steward-manage/**（mock：routes/farm-people.ts）。与管家端的 /farm/steward/** 是两个不同的前缀 */
@Tag(name = "管理后台 - 认养农场 - 管家管理")
@RestController
@RequestMapping("/farm/steward-manage")
public class FarmStewardManageController {

    @Autowired
    private FarmStewardManageService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmStewardManageRespVO>> page(FarmStewardManagePageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/overview")
    public CommonResult<Map<String, Object>> overview() {
        return success(service.overview());
    }
}
