package cn.iocoder.yudao.module.farm.controller.app.compat;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
 * 店铺装修模板没有表，返回一个写死的空画布模板（返回 null 会让前端显示「未找到模板」）。
 */
@Tag(name = "用户 App - 认养农场 - 商城前端兜底")
@RestController
@RequestMapping
public class FarmAppCompatController {

    @GetMapping("/promotion/diy-template/used")
    @PermitAll
    public CommonResult<Object> usedDiyTemplate() {
        // 前端拿到空会显示「未找到模板，请前往后台启用对应模板」（2026-11-07 在真后端浏览器里实测），所以要返回和 mock 种子一致的模板
        return success(JsonUtils.parseMap(DIY_TEMPLATE_JSON));
    }

    /** 和 mock-server/seed/diyTemplates.json 一致：首页、个人中心各一个空画布，底部导航由 sheep/config/farm.js 写死 */
    private static final String DIY_TEMPLATE_JSON = "{\"id\":1,\"name\":\"认养首页\",\"used\":true,"
            + "\"property\":{\"tabBar\":{\"theme\":\"green\",\"style\":{\"bgType\":\"color\",\"bgColor\":\"#ffffff\",\"color\":\"#8a8a8a\",\"activeColor\":\"#2e7d32\"},"
            + "\"items\":[{\"text\":\"首页\",\"url\":\"/pages/index/index\",\"iconUrl\":\"\",\"activeIconUrl\":\"\"},{\"text\":\"我的\",\"url\":\"/pages/index/user\",\"iconUrl\":\"\",\"activeIconUrl\":\"\"}]}},"
            + "\"home\":{\"page\":{\"backgroundColor\":\"#f5f7f2\"},\"navigationBar\":{\"styleType\":\"normal\",\"alwaysShow\":true,\"bgType\":\"color\",\"bgColor\":\"#2e7d32\",\"list\":[]},\"components\":[]},"
            + "\"user\":{\"page\":{\"backgroundColor\":\"#f5f7f2\"},\"navigationBar\":{\"styleType\":\"normal\",\"alwaysShow\":true,\"bgType\":\"color\",\"bgColor\":\"#2e7d32\",\"list\":[]},\"components\":[]}}";

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
