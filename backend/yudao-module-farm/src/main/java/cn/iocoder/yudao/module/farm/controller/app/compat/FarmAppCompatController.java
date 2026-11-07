package cn.iocoder.yudao.module.farm.controller.app.compat;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 芋道商城前端（app-member）启动时会请求的几个接口，认养 Demo 没有对应业务，按 mock 的返回兜底（mock：app.ts 13–20、47–48 行）。
 * 店铺装修模板没有真实实现，返回 null，前端 adaptTemplate 对 null 有处理，底部导航由 sheep/config/farm.js 写死。
 */
@Tag(name = "用户 App - 认养农场 - 商城前端兜底")
@RestController
@RequestMapping
public class FarmAppCompatController {

    @GetMapping("/promotion/diy-template/used")
    @PermitAll
    public CommonResult<Object> usedDiyTemplate() {
        return success(null);
    }

    @GetMapping("/trade/config/get")
    @PermitAll
    public CommonResult<Map<String, Object>> tradeConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brokerageEnabled", false);
        return success(m);
    }

    @GetMapping("/trade/cart/list")
    @PermitAll
    public CommonResult<Map<String, Object>> cartList() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("validList", List.of());
        m.put("invalidList", List.of());
        return success(m);
    }
}
