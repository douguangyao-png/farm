package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.farm.controller.admin.platform.vo.FarmRiskAlertHandleReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.platform.vo.FarmRiskAlertPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.platform.vo.FarmRiskAlertRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmFeedbackDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRiskAlertDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmFeedbackMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRiskAlertMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantPackageService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;
import static cn.iocoder.yudao.module.farm.service.FarmPlatformGuard.PLATFORM_TENANT_ID;

/**
 * 平台端：风控预警、总览（契约 1.2；对应 mock-server/src/routes/platform.ts 第 66–101 行）。
 * 只有平台（租户 1）能调，经营者调返回 403；数据覆盖全部租户（TenantUtils.executeIgnore）。
 */
@Service
public class FarmPlatformService {

    @Resource
    private FarmRiskAlertMapper riskAlertMapper;
    @Resource
    private FarmOrderMapper orderMapper;
    @Resource
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Resource
    private FarmAdoptionMapper adoptionMapper;
    @Resource
    private FarmFeedbackMapper feedbackMapper;
    @Resource
    private TenantService tenantService;
    @Resource
    private TenantPackageService tenantPackageService;

    // ---------- 风控预警 ----------

    public PageResult<FarmRiskAlertRespVO> alertPage(FarmRiskAlertPageReqVO req) {
        FarmPlatformGuard.requirePlatform();
        PageResult<FarmRiskAlertDO> p = TenantUtils.executeIgnore(() -> {
            return riskAlertMapper.selectPage(req, new LambdaQueryWrapperX<FarmRiskAlertDO>()
                    .eqIfPresent(FarmRiskAlertDO::getTenantId, req.getTenantId())
                    .eqIfPresent(FarmRiskAlertDO::getType, req.getType())
                    .eqIfPresent(FarmRiskAlertDO::getStatus, req.getStatus())
                    .orderByDesc(FarmRiskAlertDO::getCreateTime)
                    .orderByDesc(FarmRiskAlertDO::getId));
        });
        PageResult<FarmRiskAlertRespVO> out = BeanUtils.toBean(p, FarmRiskAlertRespVO.class);
        Map<Long, String> names = new HashMap<>();
        for (FarmRiskAlertRespVO v : out.getList()) {
            if (v.getTenantId() == null) continue;
            v.setTenantName(names.computeIfAbsent(v.getTenantId(), this::tenantName));
        }
        return out;
    }

    public void alertHandle(FarmRiskAlertHandleReqVO req) {
        FarmPlatformGuard.requirePlatform();
        FarmRiskAlertDO a = null;
        if (req.getId() != null) {
            a = TenantUtils.executeIgnore(() -> {
                return riskAlertMapper.selectById(req.getId());
            });
        }
        if (a == null) throw exception(NOT_FOUND, "预警");
        if (req.getStatus() == null || (req.getStatus() != 10 && req.getStatus() != 20)) {
            throw exception(BAD_REQUEST, "处理结果只能是已处理或已忽略");
        }
        if (StrUtil.isBlank(req.getHandleRemark())) throw exception(BAD_REQUEST, "请填写处理说明");
        FarmRiskAlertDO upd = new FarmRiskAlertDO();
        upd.setId(a.getId());
        upd.setStatus(req.getStatus());
        upd.setHandleRemark(req.getHandleRemark().trim());
        upd.setHandleTime(LocalDateTime.now());
        TenantUtils.executeIgnore(() -> {
            riskAlertMapper.updateById(upd);
        });
    }

    // ---------- 平台总览 ----------

    public Map<String, Object> overview() {
        FarmPlatformGuard.requirePlatform();
        return TenantUtils.executeIgnore(() -> {
            return doOverview();
        });
    }

    private Map<String, Object> doOverview() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<TenantDO> business = new ArrayList<>();
        for (Long id : tenantService.getTenantIdList()) {
            if (PLATFORM_TENANT_ID.equals(id)) continue;
            TenantDO t = tenantService.getTenant(id);
            if (t != null) business.add(t);
        }

        List<FarmOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>()
                .isNotNull(FarmOrderDO::getPayTime).ge(FarmOrderDO::getPayTime, monthStart));
        List<FarmSupplyOrderDO> supplyOrders = supplyOrderMapper.selectList(new LambdaQueryWrapper<FarmSupplyOrderDO>()
                .ge(FarmSupplyOrderDO::getCreateTime, monthStart));
        List<FarmAdoptionDO> adoptions = adoptionMapper.selectList(FarmAdoptionDO::getStatus, 10);

        Map<Long, Long> gmv = new HashMap<>();
        for (FarmOrderDO o : orders) gmv.merge(o.getTenantId(), o.getAmount() == null ? 0L : o.getAmount(), Long::sum);
        for (FarmSupplyOrderDO o : supplyOrders) gmv.merge(o.getTenantId(), o.getAmount() == null ? 0L : o.getAmount(), Long::sum);
        Map<Long, Long> adopting = new HashMap<>();
        for (FarmAdoptionDO a : adoptions) adopting.merge(a.getTenantId(), 1L, Long::sum);

        List<Map<String, Object>> rank = new ArrayList<>();
        long totalGmv = 0;
        for (TenantDO t : business) {
            long g = gmv.getOrDefault(t.getId(), 0L);
            totalGmv += g;
            String packageName = "";
            if (t.getPackageId() != null) {
                TenantPackageDO pkg = tenantPackageService.getTenantPackage(t.getPackageId());
                if (pkg != null && pkg.getName() != null) packageName = pkg.getName();
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenantId", t.getId());
            m.put("tenantName", t.getName());
            m.put("packageName", packageName);
            m.put("gmvMonth", g);
            m.put("adoptingCount", adopting.getOrDefault(t.getId(), 0L));
            rank.add(m);
        }
        rank.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.get("gmvMonth")).reversed());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantCount", business.size());
        out.put("activeTenantCount", business.stream().filter(t -> t.getStatus() != null && t.getStatus() == 0).count());
        out.put("gmvMonth", totalGmv);
        out.put("orderCountMonth", orders.stream().filter(o -> !PLATFORM_TENANT_ID.equals(o.getTenantId())).count());
        out.put("pendingAlertCount", riskAlertMapper.selectCount(FarmRiskAlertDO::getStatus, 0));
        out.put("feedbackPendingCount", feedbackMapper.selectCount(FarmFeedbackDO::getStatus, 0));
        out.put("tenantRank", rank);
        return out;
    }

    private String tenantName(Long id) {
        TenantDO t = tenantService.getTenant(id);
        return t == null || t.getName() == null ? "" : t.getName();
    }
}
