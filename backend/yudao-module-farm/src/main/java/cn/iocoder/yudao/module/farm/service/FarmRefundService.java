package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundAuditReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.refund.vo.FarmRefundRespVO;
import cn.iocoder.yudao.module.farm.controller.app.refund.vo.FarmAppRefundCreateReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRefundDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRefundMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;
import static cn.iocoder.yudao.module.farm.enums.FarmRefundErrorCodes.REFUND_DUPLICATE;
import static cn.iocoder.yudao.module.farm.enums.FarmRefundErrorCodes.REFUND_NOT_ALLOWED;
import static cn.iocoder.yudao.module.farm.enums.FarmRefundErrorCodes.REFUND_STATE_CHANGED;

/**
 * 退款闭环（mock：routes/farm-refund.ts，状态与联动规则见该文件顶部注释，两边必须一致）。
 * 订单状态：40 退款中 / 50 已退款；认养 30 已终止；任务 30 已关闭；代管工单 30 已取消。
 */
@Service
public class FarmRefundService {

    @Autowired
    private FarmRefundMapper refundMapper;
    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmViewLookup lookup;
    @Autowired
    private FarmAppSupport support;
    @Autowired
    private FarmNoGenerator noGenerator;

    /** 试算结果（金额单位分） */
    public record Trial(long amount, double elapsedRate, long invest, long deliver, long deduct, long refund) {
    }

    /** 与 mock trial() 一致：投入 = 实付 x 已过周期比例 x 20%（演示系数）；折算 = 实付 x min(1, 已收/约定)，无约定量不折算 */
    public static Trial trial(FarmOrderDO o, FarmAdoptionDO ad, LocalDateTime now) {
        long amount = o.getAmount() == null ? 0 : o.getAmount();
        double rate = 0;
        if (ad != null && ad.getStartAt() != null && ad.getEndAt() != null
                && now.isAfter(ad.getStartAt()) && ad.getEndAt().isAfter(ad.getStartAt())) {
            rate = Math.min(1.0, (double) Duration.between(ad.getStartAt(), now).toMillis()
                    / Duration.between(ad.getStartAt(), ad.getEndAt()).toMillis());
        }
        long invest = Math.round(amount * rate * 0.2);
        double promised = ad == null || ad.getPromisedJin() == null ? 0 : ad.getPromisedJin().doubleValue();
        double harvested = ad == null || ad.getHarvestedJin() == null ? 0 : ad.getHarvestedJin().doubleValue();
        long deliver = promised > 0 ? Math.round(amount * Math.min(1.0, harvested / promised)) : 0;
        long deduct = Math.min(amount, invest + deliver);
        return new Trial(amount, Math.round(rate * 1000) / 1000.0, invest, deliver, deduct, amount - deduct);
    }

    private static String statusName(Integer s) {
        if (s == null) return "";
        return switch (s) {
            case 0 -> "待审核";
            case 10 -> "已通过";
            case 20 -> "已驳回";
            default -> "";
        };
    }

    // =============================== 后台 ===============================

    public PageResult<FarmRefundRespVO> page(FarmRefundPageReqVO req) {
        LambdaQueryWrapperX<FarmRefundDO> w = new LambdaQueryWrapperX<>();
        w.likeIfPresent(FarmRefundDO::getNo, req.getNo()).eqIfPresent(FarmRefundDO::getStatus, req.getStatus());
        if (StrUtil.isNotEmpty(req.getOrderNo())) {
            List<Long> ids = orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>()
                            .like(FarmOrderDO::getNo, req.getOrderNo()))
                    .stream().map(FarmOrderDO::getId).collect(Collectors.toList());
            if (ids.isEmpty()) return PageResult.empty();
            w.in(FarmRefundDO::getOrderId, ids);
        }
        if (StrUtil.isNotEmpty(req.getMemberMobile())) {
            List<Long> ids = memberMapper.selectList(new LambdaQueryWrapper<FarmMemberDO>()
                            .like(FarmMemberDO::getMobile, req.getMemberMobile()))
                    .stream().map(FarmMemberDO::getId).collect(Collectors.toList());
            if (ids.isEmpty()) return PageResult.empty();
            w.in(FarmRefundDO::getMemberId, ids);
        }
        w.orderByDesc(FarmRefundDO::getCreateTime).orderByDesc(FarmRefundDO::getId);
        PageResult<FarmRefundDO> p = refundMapper.selectPage(req, w);
        return new PageResult<>(toViews(p.getList()), p.getTotal());
    }

    public Map<String, Object> stats() {
        List<FarmRefundDO> all = refundMapper.selectList(new LambdaQueryWrapper<>());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime m0 = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        long monthApply = all.stream().filter(r -> !r.getCreateTime().isBefore(m0)).count();
        Long monthPaid = orderMapper.selectCount(new LambdaQueryWrapper<FarmOrderDO>().ge(FarmOrderDO::getPayTime, m0));
        List<FarmRefundDO> pending = all.stream().filter(r -> r.getStatus() == 0).collect(Collectors.toList());
        List<FarmRefundDO> audited = all.stream().filter(r -> r.getAuditTime() != null).collect(Collectors.toList());
        List<FarmRefundDO> approved = all.stream().filter(r -> r.getStatus() == 10).collect(Collectors.toList());

        Map<String, Long> reasonCount = new LinkedHashMap<>();
        for (FarmRefundDO r : all) {
            if (!r.getCreateTime().isBefore(now.minusDays(30))) reasonCount.merge(r.getReason(), 1L, Long::sum);
        }
        Map.Entry<String, Long> top = reasonCount.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("all", (long) all.size());
        counts.put("0", (long) pending.size());
        counts.put("10", (long) approved.size());
        counts.put("20", all.stream().filter(r -> r.getStatus() == 20).count());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("monthApply", monthApply);
        out.put("refundRate", monthPaid != null && monthPaid > 0 ? Math.round((double) monthApply / monthPaid * 1000) / 10.0 : null);
        out.put("pending", (long) pending.size());
        out.put("pendingOver24h", pending.stream()
                .filter(r -> Duration.between(r.getCreateTime(), now).toMillis() > 24L * 3600_000).count());
        long sumMs = audited.stream().mapToLong(r -> Duration.between(r.getCreateTime(), r.getAuditTime()).toMillis()).sum();
        out.put("avgHandleHours", audited.isEmpty() ? null : Math.round((double) sumMs / audited.size() / 3600_000 * 10) / 10.0);
        out.put("refundedAmount", approved.stream().mapToLong(r -> r.getRefundAmount() == null ? 0 : r.getRefundAmount()).sum());
        out.put("refundedCount", (long) approved.size());
        if (top != null && top.getValue() >= 2) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("reason", top.getKey());
            alert.put("count", top.getValue());
            out.put("reasonAlert", alert);
        } else {
            out.put("reasonAlert", null);
        }
        return out;
    }

    public FarmRefundDetailRespVO get(Long id) {
        FarmRefundDO r = id == null ? null : refundMapper.selectById(id);
        if (r == null) throw exception(NOT_FOUND, "退款单");
        FarmOrderDO o = orderMapper.selectById(r.getOrderId());
        FarmAdoptionDO ad = o == null || o.getAdoptionId() == null ? null : adoptionMapper.selectById(o.getAdoptionId());
        FarmRefundDetailRespVO d = new FarmRefundDetailRespVO();
        copy(toViews(List.of(r)).get(0), d);
        if (ad != null) {
            FarmRefundDetailRespVO.Adoption a = new FarmRefundDetailRespVO.Adoption();
            a.setId(ad.getId());
            a.setStartAt(ad.getStartAt());
            a.setEndAt(ad.getEndAt());
            a.setPromisedJin(ad.getPromisedJin());
            a.setHarvestedJin(ad.getHarvestedJin());
            d.setAdoption(a);
        }
        d.setElapsedRate(o == null ? 0.0 : trial(o, ad, r.getCreateTime()).elapsedRate());
        return d;
    }

    @Transactional(rollbackFor = Exception.class)
    public FarmRefundRespVO audit(FarmRefundAuditReqVO req) {
        FarmRefundDO r = req.getId() == null ? null : refundMapper.selectById(req.getId());
        if (r == null) throw exception(NOT_FOUND, "退款单");
        if (req.getApprove() == null) throw exception(BAD_REQUEST, "请选择通过或驳回");
        boolean approve = req.getApprove();
        String remark = req.getRemark() == null ? "" : req.getRemark().trim();
        if (!approve && remark.isEmpty()) throw exception(BAD_REQUEST, "驳回时请填写审核说明");
        if (remark.length() > 200) throw exception(BAD_REQUEST, "审核说明最长 200 字");
        FarmOrderDO o = orderMapper.selectById(r.getOrderId());
        if (r.getStatus() == null || r.getStatus() != 0 || o == null || o.getStatus() == null || o.getStatus() != 40) {
            throw exception(REFUND_STATE_CHANGED);
        }
        // 条件更新抢占退款单，防止并发重复审核
        LambdaUpdateWrapper<FarmRefundDO> uw = new LambdaUpdateWrapper<FarmRefundDO>()
                .eq(FarmRefundDO::getId, r.getId()).eq(FarmRefundDO::getStatus, 0)
                .set(FarmRefundDO::getStatus, approve ? 10 : 20)
                .set(FarmRefundDO::getAuditTime, LocalDateTime.now());
        if (!remark.isEmpty()) uw.set(FarmRefundDO::getAuditRemark, remark);
        if (refundMapper.update(null, uw) == 0) throw exception(REFUND_STATE_CHANGED);

        if (!approve) {
            // 驳回：订单回到申请前的状态；认养、地块、任务不动
            orderMapper.update(null, new LambdaUpdateWrapper<FarmOrderDO>()
                    .eq(FarmOrderDO::getId, o.getId()).eq(FarmOrderDO::getStatus, 40)
                    .set(FarmOrderDO::getStatus, r.getPrevOrderStatus() == null ? 10 : r.getPrevOrderStatus()));
        } else {
            orderMapper.update(null, new LambdaUpdateWrapper<FarmOrderDO>()
                    .eq(FarmOrderDO::getId, o.getId()).eq(FarmOrderDO::getStatus, 40)
                    .set(FarmOrderDO::getStatus, 50));
            FarmAdoptionDO ad = o.getAdoptionId() == null ? null : adoptionMapper.selectById(o.getAdoptionId());
            if (ad != null) {
                adoptionMapper.update(null, new LambdaUpdateWrapper<FarmAdoptionDO>()
                        .eq(FarmAdoptionDO::getId, ad.getId()).eq(FarmAdoptionDO::getStatus, 10)
                        .set(FarmAdoptionDO::getStatus, 30));
                if (ad.getAssetId() != null) {
                    assetMapper.update(null, new LambdaUpdateWrapper<FarmAssetDO>()
                            .eq(FarmAssetDO::getId, ad.getAssetId()).eq(FarmAssetDO::getStatus, 10)
                            .set(FarmAssetDO::getStatus, 0));
                }
                taskMapper.update(null, new LambdaUpdateWrapper<FarmTaskDO>()
                        .eq(FarmTaskDO::getAdoptionId, ad.getId()).eq(FarmTaskDO::getStatus, 0)
                        .set(FarmTaskDO::getStatus, 30));
                serviceOrderMapper.update(null, new LambdaUpdateWrapper<FarmServiceOrderDO>()
                        .eq(FarmServiceOrderDO::getAdoptionId, ad.getId()).eq(FarmServiceOrderDO::getStatus, 0)
                        .set(FarmServiceOrderDO::getStatus, 30));
            }
        }
        return toViews(List.of(refundMapper.selectById(r.getId()))).get(0);
    }

    // =============================== C 端 ===============================

    public Map<String, Object> preview(Long orderId) {
        FarmMemberDO m = support.member();
        FarmOrderDO o = orderId == null ? null : orderMapper.selectById(orderId);
        if (o == null || !Objects.equals(o.getMemberId(), m.getId())) throw exception(NOT_FOUND, "订单");
        FarmAdoptionDO ad = o.getAdoptionId() == null ? null : adoptionMapper.selectById(o.getAdoptionId());
        Trial t = trial(o, ad, LocalDateTime.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", o.getId());
        out.put("orderNo", o.getNo());
        out.put("canApply", o.getStatus() != null && o.getStatus() == 10);
        out.put("orderStatus", o.getStatus());
        out.put("amount", t.amount());
        out.put("elapsedRate", t.elapsedRate());
        out.put("investDeduct", t.invest());
        out.put("deliverDeduct", t.deliver());
        out.put("deduct", t.deduct());
        out.put("refundAmount", t.refund());
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public FarmRefundRespVO create(FarmAppRefundCreateReqVO req) {
        FarmMemberDO m = support.member();
        String reason = req.getReason() == null ? "" : req.getReason().trim();
        if (reason.isEmpty()) throw exception(BAD_REQUEST, "请填写退款原因");
        if (reason.length() > 200) throw exception(BAD_REQUEST, "退款原因最长 200 字");
        FarmOrderDO o = req.getOrderId() == null ? null : orderMapper.selectById(req.getOrderId());
        if (o == null || !Objects.equals(o.getMemberId(), m.getId())) throw exception(NOT_FOUND, "订单");
        Integer st = o.getStatus();
        boolean hasPending = refundMapper.selectCount(new LambdaQueryWrapper<FarmRefundDO>()
                .eq(FarmRefundDO::getOrderId, o.getId()).eq(FarmRefundDO::getStatus, 0)) > 0;
        if ((st != null && st == 40) || hasPending) throw exception(REFUND_DUPLICATE, "该订单已有退款申请在处理中");
        if (st != null && st == 50) throw exception(REFUND_DUPLICATE, "该订单已退款");
        if (st == null || st != 10) throw exception(REFUND_NOT_ALLOWED, "该订单当前状态不能申请退款");
        FarmAdoptionDO ad = o.getAdoptionId() == null ? null : adoptionMapper.selectById(o.getAdoptionId());
        Trial t = trial(o, ad, LocalDateTime.now());
        // 先条件更新订单 10 -> 40，抢占成功才建退款单
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<FarmOrderDO>()
                .eq(FarmOrderDO::getId, o.getId()).eq(FarmOrderDO::getStatus, 10).set(FarmOrderDO::getStatus, 40));
        if (rows == 0) throw exception(REFUND_DUPLICATE, "该订单已有退款申请在处理中");
        FarmRefundDO r = new FarmRefundDO();
        r.setNo(noGenerator.next("R"));
        r.setOrderId(o.getId());
        r.setMemberId(m.getId());
        r.setAmount(t.amount());
        r.setInvestDeduct(t.invest());
        r.setDeliverDeduct(t.deliver());
        r.setDeduct(t.deduct());
        r.setRefundAmount(t.refund());
        r.setReason(reason);
        r.setStatus(0);
        r.setPrevOrderStatus(10);
        refundMapper.insert(r);
        return toViews(List.of(refundMapper.selectById(r.getId()))).get(0);
    }

    public List<FarmRefundRespVO> myList() {
        FarmMemberDO m = support.member();
        return toViews(refundMapper.selectList(new LambdaQueryWrapper<FarmRefundDO>()
                .eq(FarmRefundDO::getMemberId, m.getId())
                .orderByDesc(FarmRefundDO::getCreateTime).orderByDesc(FarmRefundDO::getId)));
    }

    // =============================== 视图 ===============================

    private List<FarmRefundRespVO> toViews(List<FarmRefundDO> list) {
        if (list.isEmpty()) return new ArrayList<>();
        Map<Long, FarmOrderDO> orders = orderMapper.selectList(FarmOrderDO::getId,
                        list.stream().map(FarmRefundDO::getOrderId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(FarmOrderDO::getId, x -> x, (a, b) -> a));
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmRefundDO::getMemberId).collect(Collectors.toList()));
        Map<Long, FarmProductDO> products = lookup.products(orders.values().stream().map(FarmOrderDO::getProductId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(orders.values().stream().map(FarmOrderDO::getAssetId).collect(Collectors.toList()));
        List<FarmRefundRespVO> out = new ArrayList<>();
        for (FarmRefundDO r : list) {
            FarmRefundRespVO v = new FarmRefundRespVO();
            FarmOrderDO o = orders.get(r.getOrderId());
            FarmMemberDO mem = members.get(r.getMemberId());
            FarmProductDO p = o == null || o.getProductId() == null ? null : products.get(o.getProductId());
            FarmAssetDO a = o == null || o.getAssetId() == null ? null : assets.get(o.getAssetId());
            v.setId(r.getId());
            v.setNo(r.getNo());
            v.setOrderId(r.getOrderId());
            v.setOrderNo(o == null || o.getNo() == null ? "" : o.getNo());
            v.setOrderStatus(o == null ? null : o.getStatus());
            v.setMemberId(r.getMemberId());
            v.setMemberName(mem == null || mem.getNickname() == null ? "" : mem.getNickname());
            v.setMemberMobile(mem == null || mem.getMobile() == null ? "" : mem.getMobile());
            v.setProductTitle(p == null || p.getTitle() == null ? "" : p.getTitle());
            v.setProductCover(p == null || p.getCover() == null ? "" : p.getCover());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            v.setCareMode(o == null ? null : o.getCareMode());
            v.setCareModeName(o == null ? "" : FarmViewLookup.careModeName(o.getCareMode()));
            v.setAmount(r.getAmount());
            v.setInvestDeduct(r.getInvestDeduct());
            v.setDeliverDeduct(r.getDeliverDeduct());
            v.setDeduct(r.getDeduct());
            v.setRefundAmount(r.getRefundAmount());
            v.setReason(r.getReason());
            v.setStatus(r.getStatus());
            v.setStatusName(statusName(r.getStatus()));
            v.setAuditRemark(r.getAuditRemark());
            v.setPrevOrderStatus(r.getPrevOrderStatus());
            v.setCreateTime(r.getCreateTime());
            v.setAuditTime(r.getAuditTime());
            out.add(v);
        }
        return out;
    }

    private static void copy(FarmRefundRespVO s, FarmRefundRespVO d) {
        d.setId(s.getId());
        d.setNo(s.getNo());
        d.setOrderId(s.getOrderId());
        d.setOrderNo(s.getOrderNo());
        d.setOrderStatus(s.getOrderStatus());
        d.setMemberId(s.getMemberId());
        d.setMemberName(s.getMemberName());
        d.setMemberMobile(s.getMemberMobile());
        d.setProductTitle(s.getProductTitle());
        d.setProductCover(s.getProductCover());
        d.setAssetCode(s.getAssetCode());
        d.setCareMode(s.getCareMode());
        d.setCareModeName(s.getCareModeName());
        d.setAmount(s.getAmount());
        d.setInvestDeduct(s.getInvestDeduct());
        d.setDeliverDeduct(s.getDeliverDeduct());
        d.setDeduct(s.getDeduct());
        d.setRefundAmount(s.getRefundAmount());
        d.setReason(s.getReason());
        d.setStatus(s.getStatus());
        d.setStatusName(s.getStatusName());
        d.setAuditRemark(s.getAuditRemark());
        d.setPrevOrderStatus(s.getPrevOrderStatus());
        d.setCreateTime(s.getCreateTime());
        d.setAuditTime(s.getAuditTime());
    }
}
