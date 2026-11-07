package cn.iocoder.yudao.module.farm.controller.admin.notice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.notice.vo.FarmNoticeListRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.notice.vo.FarmNoticeRespVO;
import cn.iocoder.yudao.module.farm.service.FarmNoticeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 平台公告：/admin-api/farm/notice/**（平台在文章管理里发布的公告，给所有经营者看） */
@Tag(name = "管理后台 - 认养农场 - 平台公告")
@RestController
@RequestMapping("/farm/notice")
public class FarmNoticeController {

    @Autowired
    private FarmNoticeService service;

    @GetMapping("/list")
    public CommonResult<List<FarmNoticeListRespVO>> list() {
        return success(service.list());
    }

    @GetMapping("/get")
    public CommonResult<FarmNoticeRespVO> get(@RequestParam("id") Long id) {
        return success(service.get(id));
    }
}
