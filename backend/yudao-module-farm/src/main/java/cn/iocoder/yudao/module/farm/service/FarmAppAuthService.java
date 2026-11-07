package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.farm.controller.app.auth.vo.FarmAppLoginReqVO;
import cn.iocoder.yudao.module.farm.controller.app.auth.vo.FarmAppLoginRespVO;
import cn.iocoder.yudao.module.farm.controller.app.auth.vo.FarmAppMemberRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;

/**
 * C 端会员登录（mock：app.ts 22–41 行）。
 * 会员是 farm_member，不是芋道后台用户；令牌借用芋道 OAuth2 令牌体系，userType = MEMBER，clientId = default。
 * 租户：farm_member 查询由租户拦截器按请求头 tenant-id 过滤；令牌里的租户来自创建时的租户上下文。
 */
@Service
public class FarmAppAuthService {

    private static final String CLIENT_ID = "default";

    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private OAuth2TokenCommonApi oauth2TokenApi;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private FarmAppSupport support;

    public FarmAppLoginRespVO login(FarmAppLoginReqVO req) {
        String mobile = req == null ? null : req.getMobile();
        String password = req == null ? null : req.getPassword();
        // 不区分「账号不存在」和「密码错」，与 mock 一致
        if (StrUtil.isBlank(mobile) || StrUtil.isEmpty(password)) throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        FarmMemberDO m = memberMapper.selectOne(FarmMemberDO::getMobile, mobile);
        if (m == null || StrUtil.isEmpty(m.getPassword()) || !passwordEncoder.matches(password, m.getPassword())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        OAuth2AccessTokenCreateReqDTO dto = new OAuth2AccessTokenCreateReqDTO();
        dto.setUserId(m.getId());
        dto.setUserType(UserTypeEnum.MEMBER.getValue());
        dto.setClientId(CLIENT_ID);
        dto.setScopes(Collections.emptyList());
        return toResp(oauth2TokenApi.createAccessToken(dto));
    }

    public void logout(String accessToken) {
        if (StrUtil.isNotBlank(accessToken)) oauth2TokenApi.removeAccessToken(accessToken);
    }

    public FarmAppLoginRespVO refreshToken(String refreshToken) {
        return toResp(oauth2TokenApi.refreshAccessToken(refreshToken, CLIENT_ID));
    }

    public FarmAppMemberRespVO currentMember() {
        FarmMemberDO m = support.member();
        FarmAppMemberRespVO v = new FarmAppMemberRespVO();
        v.setId(m.getId());
        v.setNickname(m.getNickname() == null ? "" : m.getNickname());
        v.setAvatar(m.getAvatar() == null ? "" : m.getAvatar());
        v.setMobile(m.getMobile());
        v.setAddress(m.getAddress() == null ? "" : m.getAddress());
        return v;
    }

    private static FarmAppLoginRespVO toResp(OAuth2AccessTokenRespDTO t) {
        FarmAppLoginRespVO v = new FarmAppLoginRespVO();
        v.setUserId(t.getUserId());
        v.setAccessToken(t.getAccessToken());
        v.setRefreshToken(t.getRefreshToken());
        v.setExpiresTime(t.getExpiresTime());
        return v;
    }
}
