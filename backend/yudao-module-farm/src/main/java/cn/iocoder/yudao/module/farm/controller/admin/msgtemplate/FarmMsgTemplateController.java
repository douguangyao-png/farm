package cn.iocoder.yudao.module.farm.controller.admin.msgtemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.msgtemplate.vo.FarmMsgTemplatePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.msgtemplate.vo.FarmMsgTemplateSaveReqVO;
import cn.iocoder.yudao.module.farm.service.FarmBannedWordsException;
import cn.iocoder.yudao.module.farm.service.FarmMsgTemplateService;
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

/** 消息模板 /farm/msg-template/**（第二期 B）。命中违禁词时和认养商品一样返回 {code:1030000000, data:{words, suggestion}} */
@Tag(name = "管理后台 - 认养农场 - 消息模板")
@RestController
@RequestMapping("/farm/msg-template")
public class FarmMsgTemplateController {

    @Autowired
    private FarmMsgTemplateService service;

    @GetMapping("/page")
    public CommonResult<FarmFulfillPage<Map<String, Object>>> page(FarmMsgTemplatePageReqVO req) {
        return success(service.page(req, req.getName(), req.getStatus()));
    }

    @PostMapping("/create")
    public CommonResult<Object> create(@RequestBody FarmMsgTemplateSaveReqVO req) {
        try {
            return success(service.create(req));
        } catch (FarmBannedWordsException e) {
            return e.toResult();
        }
    }

    @PutMapping("/update")
    public CommonResult<Object> update(@RequestBody FarmMsgTemplateSaveReqVO req) {
        try {
            service.update(req);
            return success(true);
        } catch (FarmBannedWordsException e) {
            return e.toResult();
        }
    }

    @PutMapping("/update-status")
    public CommonResult<Boolean> updateStatus(@RequestBody FarmMsgTemplateSaveReqVO req) {
        service.updateStatus(req.getId(), req.getStatus());
        return success(true);
    }

    @PostMapping("/test-send")
    public CommonResult<Map<String, Object>> testSend(@RequestBody FarmMsgTemplateSaveReqVO req) {
        return success(service.testSend(req.getId()));
    }
}
