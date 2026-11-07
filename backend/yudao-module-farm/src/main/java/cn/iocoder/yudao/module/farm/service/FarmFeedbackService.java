package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackReplyReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.feedback.vo.FarmFeedbackStatusReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmFeedbackDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmFeedbackMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 问题反馈（契约 2.10；对应 mock-server/src/routes/content.ts 第 99–150 行）。
 * 平台看全部租户（TenantUtils.executeIgnore），经营者只看自己租户（租户拦截器自动处理）。
 */
@Service
public class FarmFeedbackService {

    private static final List<String> TYPES = List.of("bug", "advice", "ask");
    private static final List<Integer> STATUSES = List.of(0, 10, 20, 30);

    @Resource
    private FarmFeedbackMapper feedbackMapper;
    @Resource
    private TenantService tenantService;

    public Map<String, Object> create(FarmFeedbackCreateReqVO req, String requestUserAgent) {
        if (req.getType() == null || !TYPES.contains(req.getType())) throw exception(BAD_REQUEST, "请选择反馈类型");
        if (StrUtil.isBlank(req.getContent())) throw exception(BAD_REQUEST, "请填写反馈内容");
        List<String> shots = req.getScreenshots() == null ? new ArrayList<>()
                : req.getScreenshots().stream().filter(StrUtil::isNotEmpty).collect(Collectors.toList());
        if (shots.size() > 3) throw exception(BAD_REQUEST, "最多上传 3 张截图");

        // 编号全局递增：取所有租户里当前最大编号 + 1
        int max = 0;
        List<String> allNo = TenantUtils.executeIgnore(() -> {
            return feedbackMapper.selectAllNo();
        });
        for (String no : allNo) {
            if (no != null && no.length() > 3) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(3)));
                } catch (NumberFormatException ignored) {
                    // 不是 FB-数字 的编号当作 0
                }
            }
        }

        FarmFeedbackDO f = new FarmFeedbackDO();
        f.setNo(String.format("FB-%04d", max + 1));
        f.setType(req.getType());
        f.setContent(req.getContent().trim());
        f.setScreenshots(shots);
        f.setPageTitle(StrUtil.nullToEmpty(req.getPageTitle()));
        f.setPagePath(StrUtil.nullToEmpty(req.getPagePath()));
        f.setUserAgent(StrUtil.isNotEmpty(req.getUserAgent()) ? req.getUserAgent() : StrUtil.nullToEmpty(requestUserAgent));
        Long uid = SecurityFrameworkUtils.getLoginUserId();
        f.setCreatorId(uid == null ? 0L : uid);
        f.setCreatorName(StrUtil.nullToEmpty(SecurityFrameworkUtils.getLoginUserNickname()));
        f.setStatus(0);
        f.setReplies(new ArrayList<>());
        feedbackMapper.insert(f);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", f.getId());
        out.put("no", f.getNo());
        return out;
    }

    public PageResult<FarmFeedbackRespVO> page(FarmFeedbackPageReqVO req) {
        PageResult<FarmFeedbackDO> p;
        if (FarmPlatformGuard.isPlatform()) {
            p = TenantUtils.executeIgnore(() -> {
                return feedbackMapper.selectPage(req, buildQuery(req));
            });
        } else {
            p = feedbackMapper.selectPage(req, buildQuery(req));
        }
        PageResult<FarmFeedbackRespVO> out = BeanUtils.toBean(p, FarmFeedbackRespVO.class);
        Map<Long, String> names = new java.util.HashMap<>();
        for (FarmFeedbackRespVO v : out.getList()) v.setTenantName(tenantName(names, v.getTenantId()));
        return out;
    }

    private LambdaQueryWrapperX<FarmFeedbackDO> buildQuery(FarmFeedbackPageReqVO req) {
        return new LambdaQueryWrapperX<FarmFeedbackDO>()
                .eqIfPresent(FarmFeedbackDO::getType, req.getType())
                .eqIfPresent(FarmFeedbackDO::getStatus, req.getStatus())
                .likeIfPresent(FarmFeedbackDO::getNo, req.getNo())
                .orderByDesc(FarmFeedbackDO::getCreateTime)
                .orderByDesc(FarmFeedbackDO::getId);
    }

    public FarmFeedbackRespVO get(Long id) {
        FarmFeedbackDO f = find(id);
        if (f == null) throw exception(NOT_FOUND, "反馈");
        FarmFeedbackRespVO v = BeanUtils.toBean(f, FarmFeedbackRespVO.class);
        v.setTenantName(tenantName(new java.util.HashMap<>(), v.getTenantId()));
        return v;
    }

    /** 仅平台。回复后待处理自动变处理中 */
    public void reply(FarmFeedbackReplyReqVO req) {
        FarmPlatformGuard.requirePlatform();
        FarmFeedbackDO f = find(req.getId());
        if (f == null) throw exception(NOT_FOUND, "反馈");
        if (StrUtil.isBlank(req.getContent())) throw exception(BAD_REQUEST, "请填写回复内容");
        List<Map<String, Object>> replies = f.getReplies() == null ? new ArrayList<>() : new ArrayList<>(f.getReplies());
        long now = System.currentTimeMillis();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", replies.size() + 1);
        r.put("content", req.getContent().trim());
        r.put("creatorName", StrUtil.blankToDefault(SecurityFrameworkUtils.getLoginUserNickname(), "平台"));
        r.put("createTime", now);
        replies.add(r);
        FarmFeedbackDO upd = new FarmFeedbackDO();
        upd.setId(f.getId());
        upd.setReplies(replies);
        if (f.getStatus() != null && f.getStatus() == 0) upd.setStatus(10);
        upd.setUpdateTime(LocalDateTime.now());
        updateIgnoreTenant(upd);
    }

    /** 仅平台 */
    public void updateStatus(FarmFeedbackStatusReqVO req) {
        FarmPlatformGuard.requirePlatform();
        FarmFeedbackDO f = find(req.getId());
        if (f == null) throw exception(NOT_FOUND, "反馈");
        if (req.getStatus() == null || !STATUSES.contains(req.getStatus())) throw exception(BAD_REQUEST, "状态不正确");
        FarmFeedbackDO upd = new FarmFeedbackDO();
        upd.setId(f.getId());
        upd.setStatus(req.getStatus());
        upd.setUpdateTime(LocalDateTime.now());
        updateIgnoreTenant(upd);
    }

    /** 平台看全部租户，经营者只能取到自己租户的（跨租户取不到 → 不存在） */
    private FarmFeedbackDO find(Long id) {
        if (id == null) return null;
        if (FarmPlatformGuard.isPlatform()) {
            return TenantUtils.executeIgnore(() -> {
                return feedbackMapper.selectById(id);
            });
        }
        return feedbackMapper.selectById(id);
    }

    private void updateIgnoreTenant(FarmFeedbackDO upd) {
        TenantUtils.executeIgnore(() -> {
            feedbackMapper.updateById(upd);
        });
    }

    private String tenantName(Map<Long, String> cache, Long tenantId) {
        if (tenantId == null) return "";
        return cache.computeIfAbsent(tenantId, id -> {
            TenantDO t = tenantService.getTenant(id);
            return t == null || t.getName() == null ? "" : t.getName();
        });
    }
}
