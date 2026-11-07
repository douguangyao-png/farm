package cn.iocoder.yudao.module.farm.controller.admin.article;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticlePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleSaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmArticleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.11 文章：/admin-api/promotion/article/**（接口形态沿用芋道商城） */
@Tag(name = "管理后台 - 认养农场 - 文章")
@RestController
@RequestMapping("/promotion/article")
public class FarmArticleController {

    @Autowired
    private FarmArticleService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmArticleRespVO>> page(FarmArticlePageReqVO req) {
        return success(BeanUtils.toBean(service.page(req), FarmArticleRespVO.class));
    }

    @GetMapping("/get")
    public CommonResult<FarmArticleRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(service.get(id), FarmArticleRespVO.class));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmArticleSaveReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    public CommonResult<Boolean> update(@RequestBody FarmArticleSaveReqVO req) {
        service.update(req);
        return success(true);
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        service.delete(id);
        return success(true);
    }
}
