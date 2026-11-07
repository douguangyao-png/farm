package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 接口契约 2.7 代管工单（mock：farm-admin.ts /service-order、farm/stats.ts） */
@Service
public class FarmServiceOrderService {

    /** 执行好评率没有评价数据，演示口径固定 98.4（%），与 mock 一致 */
    private static final double DEMO_GOOD_RATE = 98.4;

    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmViewLookup lookup;

    /** 列表 + 统计卡 + 页签数量（stats 字段见契约 2.7；mock：farm/stats.ts serviceOrderPage） */
    public FarmFulfillPage<FarmServiceOrderRespVO> page(FarmServiceOrderPageReqVO req) {
        List<FarmServiceOrderDO> all = serviceOrderMapper.selectList(new LambdaQueryWrapperX<FarmServiceOrderDO>()
                .orderByDesc(FarmServiceOrderDO::getCreateTime)
                .orderByDesc(FarmServiceOrderDO::getId));

        LocalDateTime m0 = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime m1 = m0.minusMonths(1);
        long monthCount = all.stream().filter(s -> !s.getCreateTime().isBefore(m0)).count();
        long lastMonthCount = all.stream().filter(s -> !s.getCreateTime().isBefore(m1) && s.getCreateTime().isBefore(m0)).count();
        List<FarmServiceOrderDO> doneThisMonth = all.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 20
                        && !(s.getFinishTime() != null ? s.getFinishTime() : s.getCreateTime()).isBefore(m0))
                .collect(Collectors.toList());
        List<FarmServiceOrderDO> accepted = all.stream().filter(s -> s.getAcceptTime() != null).collect(Collectors.toList());

        Map<String, Object> counts = FarmFulfillPage.m(
                "all", all.size(),
                "pending", countStatus(all, 0),
                "serving", countStatus(all, 10),
                "done", countStatus(all, 20),
                "cancelled", countStatus(all, 30));
        long income = 0;
        long stewardIncome = 0;
        for (FarmServiceOrderDO s : doneThisMonth) {
            long price = s.getPrice() == null ? 0L : s.getPrice();
            income += price;
            stewardIncome += Math.round(price * stewardPct(s) / 100d);
        }
        Object avgAcceptHours = null;
        if (!accepted.isEmpty()) {
            double ms = 0;
            for (FarmServiceOrderDO s : accepted) ms += Duration.between(s.getCreateTime(), s.getAcceptTime()).toMillis();
            avgAcceptHours = FarmFulfillPage.r1(ms / accepted.size() / 3600_000d);
        }
        Map<String, Object> stats = FarmFulfillPage.m(
                "counts", counts,
                "monthCount", monthCount,
                "monthCountGrowth", lastMonthCount > 0 ? FarmFulfillPage.r1((monthCount - lastMonthCount) * 100d / lastMonthCount) : null,
                "income", income,
                "stewardIncome", stewardIncome,
                "avgAcceptHours", avgAcceptHours,
                "goodRate", DEMO_GOOD_RATE);

        List<FarmServiceOrderDO> rows = all.stream()
                .filter(s -> req.getStatus() == null || req.getStatus().equals(s.getStatus()))
                .filter(s -> StrUtil.isEmpty(req.getNo()) || (s.getNo() != null && s.getNo().contains(req.getNo())))
                .collect(Collectors.toList());
        // 只对当前页补派生字段
        List<FarmServiceOrderDO> pageRows = FarmFulfillPage.slice(rows, req);
        FarmFulfillPage<FarmServiceOrderRespVO> out = new FarmFulfillPage<>();
        out.setTotal((long) rows.size());
        out.setList(toViews(pageRows));
        out.setStats(stats);
        return out;
    }

    private static long countStatus(List<FarmServiceOrderDO> list, int status) {
        return list.stream().filter(s -> s.getStatus() != null && s.getStatus() == status).count();
    }

    private static double stewardPct(FarmServiceOrderDO s) {
        Object v = s.getSplit() == null ? null : s.getSplit().get("steward");
        return v instanceof Number ? ((Number) v).doubleValue() : 0d;
    }

    public FarmServiceOrderRespVO get(Long id) {
        FarmServiceOrderDO s = id == null ? null : serviceOrderMapper.selectById(id);
        if (s == null) throw exception(NOT_FOUND, "代管工单");
        return toViews(List.of(s)).get(0);
    }

    private List<FarmServiceOrderRespVO> toViews(List<FarmServiceOrderDO> list) {
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmServiceOrderDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmServiceOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, String> users = lookup.userNames(list.stream().map(FarmServiceOrderDO::getStewardId).collect(Collectors.toList()));
        Map<Long, FarmAdoptionDO> adoptions = lookup.adoptions(list.stream().map(FarmServiceOrderDO::getAdoptionId).collect(Collectors.toList()));
        List<FarmServiceOrderRespVO> out = new ArrayList<>();
        for (FarmServiceOrderDO s : list) {
            FarmServiceOrderRespVO v = BeanUtils.toBean(s, FarmServiceOrderRespVO.class);
            FarmAssetDO a = s.getAssetId() == null ? null : assets.get(s.getAssetId());
            v.setAssetCode(a == null ? "" : a.getCode());
            FarmMemberDO m = s.getMemberId() == null ? null : members.get(s.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setStewardName(s.getStewardId() == null ? "" : users.getOrDefault(s.getStewardId(), ""));
            FarmAdoptionDO ad = s.getAdoptionId() == null ? null : adoptions.get(s.getAdoptionId());
            v.setCareModeName(ad == null ? "" : FarmViewLookup.careModeName(ad.getCareMode()));
            v.setProofCount(s.getProofPhotos() == null ? 0 : s.getProofPhotos().size());
            out.add(v);
        }
        return out;
    }
}
