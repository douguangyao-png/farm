package cn.iocoder.yudao.module.farm.controller.admin.record;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordAuditReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.service.FarmRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.6 农事记录审核：/admin-api/farm/record/** */
@Tag(name = "管理后台 - 认养农场 - 农事记录审核")
@RestController
@RequestMapping("/farm/record")
public class FarmRecordController {

    @Autowired
    private FarmRecordService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmRecordRespVO>> page(FarmRecordPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<FarmRecordRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @PutMapping("/audit")
    public CommonResult<Boolean> audit(@RequestBody FarmRecordAuditReqVO req) {
        service.audit(req);
        return success(true);
    }
}
