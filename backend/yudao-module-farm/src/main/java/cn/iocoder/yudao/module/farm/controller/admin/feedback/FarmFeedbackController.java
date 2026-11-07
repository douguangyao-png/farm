package cn.iocoder.yudao.module.farm.controller.admin.feedback;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackReplyReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackStatusReqVO;
import cn.iocoder.yudao.module.farm.service.FarmFeedbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 接口契约 2.10 问题反馈：/admin-api/farm/feedback/**（平台和经营者共用） */
@Tag(name = "管理后台 - 认养农场 - 问题反馈")
@RestController
@RequestMapping("/farm/feedback")
public class FarmFeedbackController {

    @Resource
    private FarmFeedbackService service;

    @PostMapping("/create")
    public CommonResult<Map<String, Object>> create(@RequestBody FarmFeedbackCreateReqVO req, HttpServletRequest request) {
        return success(service.create(req, request.getHeader("User-Agent")));
    }

    @GetMapping("/page")
    public CommonResult<PageResult<FarmFeedbackRespVO>> page(FarmFeedbackPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/get")
    public CommonResult<FarmFeedbackRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }

    @PostMapping("/reply")
    public CommonResult<Boolean> reply(@RequestBody FarmFeedbackReplyReqVO req) {
        service.reply(req);
        return success(true);
    }

    @PutMapping("/update-status")
    public CommonResult<Boolean> updateStatus(@RequestBody FarmFeedbackStatusReqVO req) {
        service.updateStatus(req);
        return success(true);
    }
}
