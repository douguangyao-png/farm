package cn.iocoder.yudao.module.farm.controller.app.extra;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.service.FarmAppExtraService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
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

/**
 * C 端第二期补页接口：/app-api/farm/**（mock：mock-server/src/routes/farm-app-extra.ts，注释里的行号指该文件）。
 * 搜索、基地、评价列表不需要登录（带 tenant-id）；其余需登录（芋道默认拦截，@PermitAll 的方法除外）。
 * 返回值用 Map，保持与 mock 相同的 JSON 形状。
 */
@Tag(name = "用户 App - 认养农场 - 第二期补页")
@RestController
@RequestMapping("/farm")
public class FarmAppExtraController {

    @Autowired
    private FarmAppExtraService service;

    // ---- 浏览（不需要登录）----
    @PermitAll
    @GetMapping("/search/hot")
    public CommonResult<List<String>> hot() {
        return success(service.hotWords());
    }

    @PermitAll
    @GetMapping("/search")
    public CommonResult<Map<String, Object>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                    @RequestParam(value = "type", required = false) String type,
                                                    @RequestParam(value = "sort", required = false) String sort) {
        return success(service.search(keyword, type, sort));
    }

    @PermitAll
    @GetMapping("/base/list")
    public CommonResult<List<Map<String, Object>>> baseList() {
        return success(service.baseList());
    }

    @PermitAll
    @GetMapping("/base/get")
    public CommonResult<Map<String, Object>> baseGet(@RequestParam("id") Long id) {
        return success(service.getBase(id));
    }

    @PermitAll
    @GetMapping("/review/list")
    public CommonResult<List<Map<String, Object>>> reviewList(@RequestParam(value = "productId", required = false) Long productId) {
        return success(service.reviewList(productId));
    }

    // ---- 收藏 ----
    @GetMapping("/favorite/list")
    public CommonResult<List<Map<String, Object>>> favoriteList(@RequestParam(value = "type", required = false) String type) {
        return success(service.favoriteList(type));
    }

    @GetMapping("/favorite/check")
    public CommonResult<Boolean> favoriteCheck(@RequestParam("type") String type, @RequestParam("targetId") Long targetId) {
        return success(service.favoriteCheck(type, targetId));
    }

    @PostMapping("/favorite/toggle")
    public CommonResult<Boolean> favoriteToggle(@RequestBody Map<String, Object> req) {
        return success(service.favoriteToggle(String.valueOf(req.get("type")), Long.valueOf(String.valueOf(req.get("targetId")))));
    }

    // ---- 收货地址 ----
    @GetMapping("/address/list")
    public CommonResult<List<Map<String, Object>>> addressList() {
        return success(service.addressList());
    }

    @PostMapping("/address/save")
    public CommonResult<Long> addressSave(@RequestBody Map<String, Object> req) {
        return success(service.addressSave(req));
    }

    @PostMapping("/address/delete")
    public CommonResult<Boolean> addressDelete(@RequestBody Map<String, Object> req) {
        return success(service.addressDelete(Long.valueOf(String.valueOf(req.get("id")))));
    }

    // ---- 消息 ----
    @GetMapping("/message/list")
    public CommonResult<Map<String, Object>> messageList(@RequestParam(value = "type", required = false) String type) {
        return success(service.messageList(type));
    }

    @PostMapping("/message/read")
    public CommonResult<Boolean> messageRead(@RequestBody(required = false) Map<String, Object> req) {
        return success(service.messageRead(req == null || req.get("key") == null ? null : String.valueOf(req.get("key"))));
    }

    // ---- 详情类 ----
    @GetMapping("/record/get")
    public CommonResult<Map<String, Object>> recordGet(@RequestParam("id") Long id) {
        return success(service.getRecord(id));
    }

    @GetMapping("/report/get")
    public CommonResult<Map<String, Object>> reportGet(@RequestParam("adoptionId") Long adoptionId) {
        return success(service.getReport(adoptionId));
    }

    @GetMapping("/logistics/get")
    public CommonResult<Map<String, Object>> logisticsGet(@RequestParam("id") Long id) {
        return success(service.getLogistics(id));
    }

    // ---- 转赠、评价、预约、管家 ----
    @GetMapping("/transfer/list")
    public CommonResult<List<Map<String, Object>>> transferList() {
        return success(service.transferList());
    }

    @PostMapping("/transfer/create")
    public CommonResult<Map<String, Object>> transferCreate(@RequestBody Map<String, Object> req) {
        return success(service.transferCreate(req));
    }

    @GetMapping("/review/mine")
    public CommonResult<List<Map<String, Object>>> reviewMine() {
        return success(service.reviewMine());
    }

    @PostMapping("/review/create")
    public CommonResult<Long> reviewCreate(@RequestBody Map<String, Object> req) {
        return success(service.reviewCreate(req));
    }

    @GetMapping("/visit/list")
    public CommonResult<List<Map<String, Object>>> visitList() {
        return success(service.visitList());
    }

    @PostMapping("/visit/create")
    public CommonResult<Map<String, Object>> visitCreate(@RequestBody Map<String, Object> req) {
        return success(service.visitCreate(req));
    }

    @GetMapping("/service/messages")
    public CommonResult<Map<String, Object>> serviceMessages() {
        return success(service.serviceMessages());
    }

    @PostMapping("/service/send")
    public CommonResult<Boolean> serviceSend(@RequestBody Map<String, Object> req) {
        return success(service.serviceSend(req.get("content") == null ? null : String.valueOf(req.get("content"))));
    }
}
