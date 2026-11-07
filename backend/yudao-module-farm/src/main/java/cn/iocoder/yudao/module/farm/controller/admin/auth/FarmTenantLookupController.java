package cn.iocoder.yudao.module.farm.controller.admin.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录页不选租户：按用户名找租户编号（用户 2026-11-05 定，用户名全平台唯一；开通租户时校验唯一）。
 * 与 mock 的 GET /system/tenant/get-id-by-username 一致：找不到返回 null，前端提示「账号或密码不正确」，
 * 不区分「账号不存在」和「密码错」，避免被用来探测账号。
 * 免登录，也免租户请求头（见 application-farm.yaml 的 yudao.tenant.ignore-urls）。
 */
@Tag(name = "管理后台 - 认养农场 - 登录")
@RestController
@RequestMapping("/system/tenant")
public class FarmTenantLookupController {

    @Autowired
    private AdminUserMapper userMapper;

    @GetMapping("/get-id-by-username")
    @PermitAll
    public CommonResult<Long> getTenantIdByUsername(@RequestParam("username") String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) return CommonResult.success(null);
        AdminUserDO user = TenantUtils.executeIgnore(() -> userMapper.selectByUsername(name));
        return CommonResult.success(user == null ? null : user.getTenantId());
    }
}
