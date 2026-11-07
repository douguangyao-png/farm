package cn.iocoder.yudao.module.farm.controller.admin.finance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.finance.vo.FarmFlowPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.service.FarmAnalysisService;
import cn.iocoder.yudao.module.farm.service.FarmFinanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 财务（交易流水、分账结算）与经营分析：/admin-api/farm/finance/**、/admin-api/farm/analysis/**（第二期 B，契约见 docs/plans/第二期-登记/B.md） */
@Tag(name = "管理后台 - 认养农场 - 财务与经营分析")
@RestController
@RequestMapping("/farm")
public class FarmFinanceController {

    @Autowired
    private FarmFinanceService financeService;
    @Autowired
    private FarmAnalysisService analysisService;

    @GetMapping("/finance/flow/page")
    public CommonResult<FarmFulfillPage<Map<String, Object>>> flowPage(FarmFlowPageReqVO req) {
        return success(financeService.flowPage(req, req.getTradeNo(), req.getMemberName(), req.getType(), req.getSettleStatus(), req.getBeginTime(), req.getEndTime()));
    }

    @GetMapping("/finance/split/get")
    public CommonResult<Map<String, Object>> split() {
        return success(financeService.split());
    }

    @GetMapping("/analysis/summary")
    public CommonResult<Map<String, Object>> analysis(@RequestParam(value = "days", required = false, defaultValue = "30") Integer days) {
        return success(analysisService.summary(days));
    }
}
