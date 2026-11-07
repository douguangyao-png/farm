package cn.iocoder.yudao.module.farm.controller.app.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.app.order.vo.FarmAppIdReqVO;
import cn.iocoder.yudao.module.farm.controller.app.order.vo.FarmAppOrderCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.app.order.vo.FarmAppOrderDetailRespVO;
import cn.iocoder.yudao.module.farm.service.FarmAppOrderService;
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

/** C 端认养订单（需登录）：/app-api/farm/order/**（mock：farm-app.ts 60–144 行） */
@Tag(name = "用户 App - 认养农场 - 认养订单")
@RestController
@RequestMapping("/farm/order")
public class FarmAppOrderController {

    @Autowired
    private FarmAppOrderService service;

    /** mock 61 行 */
    @PostMapping("/create")
    public CommonResult<Map<String, Object>> create(@RequestBody FarmAppOrderCreateReqVO req) {
        return success(service.create(req));
    }

    /** mock 99 行 */
    @PostMapping("/pay")
    public CommonResult<Map<String, Object>> pay(@RequestBody FarmAppIdReqVO req) {
        return success(service.pay(req.getId()));
    }

    /** mock 135 行 */
    @GetMapping("/get")
    public CommonResult<FarmAppOrderDetailRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    /** mock 141 行 */
    @GetMapping("/list")
    public CommonResult<List<FarmAppOrderDetailRespVO>> list() {
        return success(service.list());
    }
}
