package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * C 端公共能力：当前会员、"我的认养"校验、演示占位图（mock：farm-app.ts 13 行 mine()、myAdoption()；store.ts placeholderImage）。
 * 租户隔离由芋道拦截器自动追加 tenant_id，所以按 id 取到的数据一定是本租户的（对应 mock 的 own()）。
 */
@Component
public class FarmAppSupport {

    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;

    /** 当前登录会员编号；未登录抛 401（正常情况下 Spring Security 已先拦截） */
    public Long memberId() {
        Long id = SecurityFrameworkUtils.getLoginUserId();
        if (id == null) throw exception(GlobalErrorCodeConstants.UNAUTHORIZED);
        return id;
    }

    /** 当前登录会员记录（对应 mock currentMember）；会员已不存在按未登录处理 */
    public FarmMemberDO member() {
        FarmMemberDO m = memberMapper.selectById(memberId());
        if (m == null) throw exception(GlobalErrorCodeConstants.UNAUTHORIZED);
        return m;
    }

    /** 我的认养（对应 mock myAdoption）：不是自己的或不存在返回 null */
    public FarmAdoptionDO myAdoption(Long id) {
        if (id == null) return null;
        FarmAdoptionDO ad = adoptionMapper.selectById(id);
        return ad != null && Objects.equals(ad.getMemberId(), memberId()) ? ad : null;
    }

    public static long toMillis(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 与 mock placeholderImage 逐字符一致（含 encodeURIComponent 的转义规则） */
    public static String placeholderImage(String label, String color) {
        String safe = label == null ? "" : label.replaceAll("[<>&\"']", "");
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"300\" viewBox=\"0 0 400 300\">"
                + "<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\"><stop offset=\"0\" stop-color=\"" + color + "\"/>"
                + "<stop offset=\"1\" stop-color=\"" + color + "\" stop-opacity=\"0.65\"/></linearGradient></defs>"
                + "<rect width=\"400\" height=\"300\" fill=\"url(#g)\"/>"
                + "<text x=\"200\" y=\"160\" font-size=\"34\" font-family=\"sans-serif\" fill=\"#fff\" text-anchor=\"middle\">" + safe + "</text>"
                + "<text x=\"200\" y=\"200\" font-size=\"14\" font-family=\"sans-serif\" fill=\"#ffffffcc\" text-anchor=\"middle\">演示图片</text></svg>";
        String enc = URLEncoder.encode(svg, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%21", "!").replace("%27", "'")
                .replace("%28", "(").replace("%29", ")").replace("%7E", "~");
        return "data:image/svg+xml;charset=utf-8," + enc;
    }
}
