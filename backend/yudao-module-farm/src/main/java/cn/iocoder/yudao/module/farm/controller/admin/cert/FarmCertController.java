package cn.iocoder.yudao.module.farm.controller.admin.cert;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.cert.vo.FarmCertPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.cert.vo.FarmCertSaveReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.fundnotice.vo.FarmFundNoticeSaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmCertService;
import cn.iocoder.yudao.module.farm.service.FarmFundNoticeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 合规中心：资质证照 /farm/cert/**、资金公示 /farm/fund-notice/**（第二期 B） */
@Tag(name = "管理后台 - 认养农场 - 合规中心")
@RestController
@RequestMapping("/farm")
public class FarmCertController {

    @Autowired
    private FarmCertService certService;
    @Autowired
    private FarmFundNoticeService fundNoticeService;

    @GetMapping("/cert/page")
    public CommonResult<FarmFulfillPage<Map<String, Object>>> certPage(FarmCertPageReqVO req) {
        return success(certService.page(req, req.getName(), req.getType()));
    }

    @PostMapping("/cert/create")
    public CommonResult<Long> certCreate(@RequestBody FarmCertSaveReqVO req) {
        return success(certService.create(req));
    }

    @PutMapping("/cert/update-public")
    public CommonResult<Boolean> certUpdatePublic(@RequestBody FarmCertSaveReqVO req) {
        certService.updatePublic(req.getId(), req.getPublicMode());
        return success(true);
    }

    @GetMapping("/fund-notice/get")
    public CommonResult<Map<String, Object>> fundNoticeGet() {
        return success(fundNoticeService.get());
    }

    @PutMapping("/fund-notice/save")
    public CommonResult<Boolean> fundNoticeSave(@RequestBody FarmFundNoticeSaveReqVO req) {
        fundNoticeService.save(req.getFields());
        return success(true);
    }
}
