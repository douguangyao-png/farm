package cn.iocoder.yudao.module.farm.controller.app.adoption;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderAdoptionVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentRespVO;
import cn.iocoder.yudao.module.farm.controller.app.adoption.vo.FarmAppAdoptionDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.app.adoption.vo.FarmAppCameraRespVO;
import cn.iocoder.yudao.module.farm.controller.app.adoption.vo.FarmAppTraceRespVO;
import cn.iocoder.yudao.module.farm.service.FarmAppAdoptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** C 端我的认养 + 收获配送（需登录）：/app-api/farm/adoption/**、/app-api/farm/shipment/list（mock：farm-app.ts 146–194、271–277 行） */
@Tag(name = "用户 App - 认养农场 - 我的认养")
@RestController
@RequestMapping("/farm")
public class FarmAppAdoptionController {

    @Autowired
    private FarmAppAdoptionService service;

    /** mock 152 行 */
    @GetMapping("/adoption/list")
    public CommonResult<List<FarmOrderAdoptionVO>> list() {
        return success(service.list());
    }

    /** mock 156 行 */
    @GetMapping("/adoption/get")
    public CommonResult<FarmAppAdoptionDetailRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    /** mock 160 行 */
    @GetMapping("/adoption/records")
    public CommonResult<List<FarmRecordRespVO>> records(@RequestParam("adoptionId") Long adoptionId) {
        return success(service.records(adoptionId));
    }

    /** mock 165 行 */
    @GetMapping("/adoption/trace")
    public CommonResult<FarmAppTraceRespVO> trace(@RequestParam("adoptionId") Long adoptionId) {
        return success(service.trace(adoptionId));
    }

    /** mock 184 行 */
    @GetMapping("/adoption/env")
    public CommonResult<Map<String, Object>> env(@RequestParam("adoptionId") Long adoptionId) {
        return success(service.env(adoptionId));
    }

    /** mock 189 行 */
    @GetMapping("/adoption/camera")
    public CommonResult<FarmAppCameraRespVO> camera(@RequestParam("adoptionId") Long adoptionId) {
        return success(service.camera(adoptionId));
    }

    /** mock 272 行 */
    @GetMapping("/shipment/list")
    public CommonResult<List<FarmShipmentRespVO>> shipments(
            @RequestParam(value = "adoptionId", required = false) Long adoptionId) {
        return success(service.shipments(adoptionId));
    }
}
