package cn.iocoder.yudao.module.farm.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.device.vo.FarmDevicePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.device.vo.FarmDeviceSaveReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.service.FarmDeviceService;
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

/** IoT 设备 /farm/device/**、视频监控 /farm/camera/**（第二期 B；状态、上报时间、固件为演示数据） */
@Tag(name = "管理后台 - 认养农场 - IoT 设备与视频监控")
@RestController
@RequestMapping("/farm")
public class FarmDeviceController {

    @Autowired
    private FarmDeviceService service;

    @GetMapping("/device/page")
    public CommonResult<FarmFulfillPage<Map<String, Object>>> page(FarmDevicePageReqVO req) {
        return success(service.page(req, req.getCode(), req.getKind(), req.getStatus()));
    }

    @PostMapping("/device/create")
    public CommonResult<String> create(@RequestBody FarmDeviceSaveReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/device/update")
    public CommonResult<Boolean> update(@RequestBody FarmDeviceSaveReqVO req) {
        service.update(req);
        return success(true);
    }

    @PutMapping("/device/dispatch")
    public CommonResult<Boolean> dispatch(@RequestBody FarmDeviceSaveReqVO req) {
        service.dispatch(req.getCode());
        return success(true);
    }

    @GetMapping("/camera/overview")
    public CommonResult<Map<String, Object>> cameraOverview() {
        return success(service.cameraOverview());
    }
}
