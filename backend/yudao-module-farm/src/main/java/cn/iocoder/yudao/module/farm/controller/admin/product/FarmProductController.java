package cn.iocoder.yudao.module.farm.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductSaveReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductStatusReqVO;
import cn.iocoder.yudao.module.farm.service.FarmBannedWordsException;
import cn.iocoder.yudao.module.farm.service.FarmProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.4 认养商品：/admin-api/farm/product/** */
@Tag(name = "管理后台 - 认养农场 - 认养商品")
@RestController
@RequestMapping("/farm/product")
public class FarmProductController {

    @Resource
    private FarmProductService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmProductRespVO>> page(FarmProductPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<FarmProductRespVO> get(@RequestParam("id") Long id) {
        return success(service.getView(id));
    }

    /** 命中违禁词时返回 {code:1030000000, msg, data:{words, suggestion}}；ServiceException 带不了 data，所以在这里捕获 */
    @PostMapping("/create")
    public CommonResult<Object> create(@RequestBody FarmProductSaveReqVO req) {
        try {
            return success(service.create(req));
        } catch (FarmBannedWordsException e) {
            return e.toResult();
        }
    }

    @PutMapping("/update")
    public CommonResult<Object> update(@RequestBody FarmProductSaveReqVO req) {
        try {
            service.update(req);
            return success(true);
        } catch (FarmBannedWordsException e) {
            return e.toResult();
        }
    }

    @PutMapping("/update-status")
    public CommonResult<Boolean> updateStatus(@RequestBody FarmProductStatusReqVO req) {
        service.updateStatus(req.getId(), req.getStatus());
        return success(true);
    }
}
