package cn.iocoder.yudao.module.farm.controller.app.selftask;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.app.order.vo.FarmAppIdReqVO;
import cn.iocoder.yudao.module.farm.controller.app.selftask.vo.FarmAppSelfTaskRespVO;
import cn.iocoder.yudao.module.farm.service.FarmAppSelfTaskService;
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

/** C 端自管任务（需登录）：/app-api/farm/self-task/**（mock：farm-app.ts 196–236 行） */
@Tag(name = "用户 App - 认养农场 - 自管任务")
@RestController
@RequestMapping("/farm/self-task")
public class FarmAppSelfTaskController {

    @Autowired
    private FarmAppSelfTaskService service;

    /** mock 197 行 */
    @GetMapping("/list")
    public CommonResult<List<FarmAppSelfTaskRespVO>> list(
            @RequestParam(value = "adoptionId", required = false) Long adoptionId,
            @RequestParam(value = "status", required = false) Integer status) {
        return success(service.list(adoptionId, status));
    }

    /** mock 216 行 */
    @PostMapping("/done")
    public CommonResult<Boolean> done(@RequestBody FarmAppIdReqVO req) {
        return success(service.done(req.getId()));
    }

    /** mock 224 行 */
    @PostMapping("/entrust")
    public CommonResult<Map<String, Object>> entrust(@RequestBody FarmAppIdReqVO req) {
        return success(service.entrust(req.getId()));
    }
}
