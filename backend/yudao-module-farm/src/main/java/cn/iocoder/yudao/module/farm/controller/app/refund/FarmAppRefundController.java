package cn.iocoder.yudao.module.farm.controller.app.refund;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundRespVO;
import cn.iocoder.yudao.module.farm.controller.app.refund.vo.FarmAppRefundCreateReqVO;
import cn.iocoder.yudao.module.farm.service.FarmRefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** C 端退款（需登录）：/app-api/farm/refund/**（mock：routes/farm-refund.ts farmAppRefundRouter） */
@Tag(name = "用户 App - 认养农场 - 退款")
@RestController
@RequestMapping("/farm/refund")
public class FarmAppRefundController {

    @Autowired
    private FarmRefundService service;

    @GetMapping("/preview")
    public CommonResult<Map<String, Object>> preview(@RequestParam("orderId") Long orderId) {
        return success(service.preview(orderId));
    }

    @PostMapping("/create")
    public CommonResult<FarmRefundRespVO> create(@RequestBody FarmAppRefundCreateReqVO req) {
        return success(service.create(req));
    }

    @GetMapping("/list")
    public CommonResult<List<FarmRefundRespVO>> list() {
        return success(service.myList());
    }
}
