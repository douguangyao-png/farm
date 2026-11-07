package cn.iocoder.yudao.module.farm.controller.app.auth;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.farm.controller.app.auth.vo.FarmAppLoginReqVO;
import cn.iocoder.yudao.module.farm.controller.app.auth.vo.FarmAppLoginRespVO;
import cn.iocoder.yudao.module.farm.controller.app.auth.vo.FarmAppMemberRespVO;
import cn.iocoder.yudao.module.farm.service.FarmAppAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** C 端会员登录：/app-api/member/**（路径与前端 sheep/api/member 一致，mock：app.ts 22–41 行） */
@Tag(name = "用户 App - 认养农场 - 会员登录")
@RestController
@RequestMapping("/member")
public class FarmAppAuthController {

    @Autowired
    private FarmAppAuthService service;
    @Autowired
    private SecurityProperties securityProperties;

    @PostMapping("/auth/login")
    @PermitAll
    public CommonResult<FarmAppLoginRespVO> login(@RequestBody FarmAppLoginReqVO req) {
        return success(service.login(req));
    }

    @PostMapping("/auth/logout")
    @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        service.logout(token);
        return success(true);
    }

    @PostMapping("/auth/refresh-token")
    @PermitAll
    public CommonResult<FarmAppLoginRespVO> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return success(service.refreshToken(refreshToken));
    }

    @GetMapping("/user/get")
    public CommonResult<FarmAppMemberRespVO> getUser() {
        return success(service.currentMember());
    }
}
