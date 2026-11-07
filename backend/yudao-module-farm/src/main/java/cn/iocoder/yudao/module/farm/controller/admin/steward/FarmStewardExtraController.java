package cn.iocoder.yudao.module.farm.controller.admin.steward;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentCreateReqVO;
import cn.iocoder.yudao.module.farm.service.FarmStewardExtraService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管家端第二期补充：/admin-api/farm/steward/**（mock：farm-steward-extra.ts）。
 * 与 FarmStewardController 同前缀、路径不重复；只看、只做自己负责的认养。
 */
@Tag(name = "管理后台 - 认养农场 - 管家端（补充）")
@RestController
@RequestMapping("/farm/steward")
public class FarmStewardExtraController {

    @Autowired
    private FarmStewardExtraService service;

    // 配送确认
    @GetMapping("/shipment/list")
    public CommonResult<Map<String, Object>> shipmentList(@RequestParam(value = "status", required = false) Integer status) {
        return success(service.shipmentList(status));
    }

    @PostMapping("/shipment/create")
    public CommonResult<Long> shipmentCreate(@RequestBody FarmShipmentCreateReqVO req) {
        return success(service.shipmentCreate(req));
    }

    /** body: { id } 或 { no }（扫码得到的配送单号） */
    @PutMapping("/shipment/sign")
    public CommonResult<Boolean> shipmentSign(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        service.shipmentSign(id, body.get("no") == null ? null : String.valueOf(body.get("no")));
        return success(true);
    }

    // 自管指导
    @GetMapping("/self-guide/list")
    public CommonResult<Map<String, Object>> selfGuideList(@RequestParam(value = "state", required = false) String state) {
        return success(service.selfGuideList(state));
    }

    @PostMapping("/self-guide/remind")
    public CommonResult<Boolean> selfGuideRemind(@RequestBody Map<String, Object> body) {
        service.selfGuideRemind(body.get("taskId") == null ? null : Long.valueOf(String.valueOf(body.get("taskId"))),
                Boolean.parseBoolean(String.valueOf(body.get("entrust"))));
        return success(true);
    }

    // 我的记录
    @GetMapping("/record/list")
    public CommonResult<Map<String, Object>> recordList(@RequestParam(value = "status", required = false) Integer status) {
        return success(service.recordList(status));
    }

    // 标的管理
    @GetMapping("/asset/list")
    public CommonResult<Map<String, Object>> assetList(@RequestParam(value = "scope", required = false) String scope) {
        return success(service.assetList(scope));
    }

    // 农资代办
    @GetMapping("/supply-task/list")
    public CommonResult<Map<String, Object>> supplyTaskList(@RequestParam(value = "tab", required = false) String tab) {
        return success(service.supplyTaskList(tab));
    }

    @PutMapping("/supply-task/apply")
    public CommonResult<Boolean> supplyTaskApply(@RequestBody Map<String, Object> body) {
        service.supplyTaskApply(body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id"))),
                body.get("dosage") == null ? null : String.valueOf(body.get("dosage")),
                body.get("usedAt") == null ? null : Long.valueOf(String.valueOf(body.get("usedAt"))));
        return success(true);
    }

    // 异常上报
    @GetMapping("/alarm/list")
    public CommonResult<List<Map<String, Object>>> alarmList() {
        return success(service.alarmList());
    }

    @PostMapping("/alarm/create")
    public CommonResult<Long> alarmCreate(@RequestBody Map<String, Object> body) {
        return success(service.alarmCreate(body));
    }

    // 消息 / 聊天
    @GetMapping("/message/conversations")
    public CommonResult<Map<String, Object>> conversations() {
        return success(service.conversations());
    }

    @GetMapping("/message/list")
    public CommonResult<Map<String, Object>> messageList(@RequestParam("memberId") Long memberId) {
        return success(service.messageList(memberId));
    }

    @PostMapping("/message/send")
    public CommonResult<Boolean> messageSend(@RequestBody Map<String, Object> body) {
        service.messageSend(body.get("memberId") == null ? null : Long.valueOf(String.valueOf(body.get("memberId"))),
                body.get("content") == null ? null : String.valueOf(body.get("content")));
        return success(true);
    }

    // 绩效
    @GetMapping("/performance")
    public CommonResult<Map<String, Object>> performance() {
        return success(service.performance());
    }
}
