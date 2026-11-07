package cn.iocoder.yudao.module.farm.controller.admin.asset;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetSaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmAssetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.3 资产：/admin-api/farm/asset/** */
@Tag(name = "管理后台 - 认养农场 - 资产")
@RestController
@RequestMapping("/farm/asset")
public class FarmAssetController {

    @Autowired
    private FarmAssetService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmAssetRespVO>> page(FarmAssetPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<FarmAssetRespVO> get(@RequestParam("id") Long id) {
        return success(service.getView(id));
    }

    @GetMapping("/env")
    public CommonResult<Map<String, Object>> env(@RequestParam("assetId") Long assetId) {
        return success(service.env(assetId));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmAssetSaveReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    public CommonResult<Boolean> update(@RequestBody FarmAssetSaveReqVO req) {
        service.update(req);
        return success(true);
    }
}
