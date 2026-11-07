package cn.iocoder.yudao.module.farm.controller.admin.supply;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplyPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplyRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplySaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmBannedWordsException;
import cn.iocoder.yudao.module.farm.service.FarmSupplyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.8 农资商品：/admin-api/farm/supply/** */
@Tag(name = "管理后台 - 认养农场 - 农资商品")
@RestController
@RequestMapping("/farm/supply")
public class FarmSupplyController {

    @Autowired
    private FarmSupplyService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmSupplyRespVO>> page(FarmSupplyPageReqVO req) {
        return success(BeanUtils.toBean(service.page(req), FarmSupplyRespVO.class));
    }

    @GetMapping("/get")
    public CommonResult<FarmSupplyRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(service.get(id), FarmSupplyRespVO.class));
    }

    /** 命中违禁词时返回 {code:1030000000, msg, data:{words, suggestion}}（农资不记命中记录，与 mock 一致） */
    @PostMapping("/create")
    public CommonResult<Object> create(@RequestBody FarmSupplySaveReqVO req) {
        try {
            return success(service.create(req));
        } catch (FarmBannedWordsException e) {
            return e.toResult();
        }
    }

    @PutMapping("/update")
    public CommonResult<Object> update(@RequestBody FarmSupplySaveReqVO req) {
        try {
            service.update(req);
            return success(true);
        } catch (FarmBannedWordsException e) {
            return e.toResult();
        }
    }
}
