package cn.iocoder.yudao.module.farm.controller.admin.article;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleCategoryPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleCategoryRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleCategorySaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmArticleCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.11 文章分类：/admin-api/promotion/article-category/**（接口形态沿用芋道商城） */
@Tag(name = "管理后台 - 认养农场 - 文章分类")
@RestController
@RequestMapping("/promotion/article-category")
public class FarmArticleCategoryController {

    @Resource
    private FarmArticleCategoryService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmArticleCategoryRespVO>> page(FarmArticleCategoryPageReqVO req) {
        return success(BeanUtils.toBean(service.page(req), FarmArticleCategoryRespVO.class));
    }

    @GetMapping("/list-all-simple")
    public CommonResult<List<Map<String, Object>>> listAllSimple() {
        return success(service.listEnabled().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/get")
    public CommonResult<FarmArticleCategoryRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(service.get(id), FarmArticleCategoryRespVO.class));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmArticleCategorySaveReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    public CommonResult<Boolean> update(@RequestBody FarmArticleCategorySaveReqVO req) {
        service.update(req);
        return success(true);
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        service.delete(id);
        return success(true);
    }
}
