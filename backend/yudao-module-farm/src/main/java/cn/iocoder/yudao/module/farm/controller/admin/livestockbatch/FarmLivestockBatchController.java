package cn.iocoder.yudao.module.farm.controller.admin.livestockbatch;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo.FarmLivestockBatchOutReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo.FarmLivestockBatchPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo.FarmLivestockBatchSaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmLivestockBatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 畜禽批次：/admin-api/farm/livestock-batch/**（第一期补齐） */
@Tag(name = "管理后台 - 认养农场 - 畜禽批次")
@RestController
@RequestMapping("/farm/livestock-batch")
public class FarmLivestockBatchController {

    @Autowired
    private FarmLivestockBatchService service;

    @GetMapping("/page")
    public CommonResult<PageResult<Map<String, Object>>> page(FarmLivestockBatchPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @PostMapping("/create")
    public CommonResult<Long> create(@RequestBody FarmLivestockBatchSaveReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    public CommonResult<Boolean> update(@RequestBody FarmLivestockBatchSaveReqVO req) {
        service.update(req);
        return success(true);
    }

    @PutMapping("/out")
    public CommonResult<Boolean> out(@RequestBody FarmLivestockBatchOutReqVO req) {
        service.out(req);
        return success(true);
    }

    @DeleteMapping("/delete")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        service.delete(id);
        return success(true);
    }
}
