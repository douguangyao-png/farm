package cn.iocoder.yudao.module.farm.controller.admin.base;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.base.vo.FarmBasePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.base.vo.FarmBaseRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.base.vo.FarmBaseSaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmBaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.2 基地：/admin-api/farm/base/** */
@Tag(name = "管理后台 - 认养农场 - 基地")
@RestController
@RequestMapping("/farm/base")
public class FarmBaseController {

    @Autowired
    private FarmBaseService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmBaseRespVO>> page(FarmBasePageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/list")
    public CommonResult<List<Map<String, Object>>> list() {
        return success(service.listEnabled().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getName());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/get")
    public CommonResult<FarmBaseRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(service.get(id), FarmBaseRespVO.class));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmBaseSaveReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    public CommonResult<Boolean> update(@RequestBody FarmBaseSaveReqVO req) {
        service.update(req);
        return success(true);
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        service.delete(id);
        return success(true);
    }
}
