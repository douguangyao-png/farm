package cn.iocoder.yudao.module.farm.controller.app.supplyorder;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderRespVO;
import cn.iocoder.yudao.module.farm.controller.app.supplyorder.vo.FarmAppSupplyOrderCreateReqVO;
import cn.iocoder.yudao.module.farm.service.FarmAppSupplyOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** C 端农资订单（需登录）：/app-api/farm/supply-order/**（mock：farm-app.ts 238–269 行） */
@Tag(name = "用户 App - 认养农场 - 农资订单")
@RestController
@RequestMapping("/farm/supply-order")
public class FarmAppSupplyOrderController {

    @Autowired
    private FarmAppSupplyOrderService service;

    /** mock 239 行 */
    @PostMapping("/create")
    public CommonResult<Map<String, Object>> create(@RequestBody FarmAppSupplyOrderCreateReqVO req) {
        return success(service.create(req));
    }

    /** mock 266 行 */
    @GetMapping("/list")
    public CommonResult<List<FarmSupplyOrderRespVO>> list() {
        return success(service.list());
    }
}
