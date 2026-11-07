package cn.iocoder.yudao.module.farm.controller.admin.refund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundAuditReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundRespVO;
import cn.iocoder.yudao.module.farm.service.FarmRefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 退款审核：/admin-api/farm/refund/**（mock：routes/farm-refund.ts） */
@Tag(name = "管理后台 - 认养农场 - 退款审核")
@RestController
@RequestMapping("/farm/refund")
public class FarmRefundController {

    @Autowired
    private FarmRefundService service;

    @GetMapping("/page")
    public CommonResult<PageResult<FarmRefundRespVO>> page(FarmRefundPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/stats")
    public CommonResult<Map<String, Object>> stats() {
        return success(service.stats());
    }

    @GetMapping("/get")
    public CommonResult<FarmRefundDetailRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @PutMapping("/audit")
    public CommonResult<FarmRefundRespVO> audit(@RequestBody FarmRefundAuditReqVO req) {
        return success(service.audit(req));
    }
}
