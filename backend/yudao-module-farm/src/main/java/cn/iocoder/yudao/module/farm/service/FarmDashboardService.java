package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.dashboard.vo.FarmRecordViewVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmHarvestMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作台（契约 2.1；对应 mock-server/src/routes/farm-admin.ts 的 dashboard/summary）。
 * 租户隔离由芋道租户拦截器自动处理，这里不再手写 tenant_id。
 */
@Service
public class FarmDashboardService {

    private static final long DAY = 86400_000L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmHarvestMapper harvestMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private AdminUserApi adminUserApi;
    @Autowired
    private FarmRiskService riskService;
    @Autowired
    private FarmViewLookup lookup;

    /** 资产类型 -> 认养分类名（与 mock views.ts 的 CATEGORY_NAME 一致） */
    static final Map<String, String> CATEGORY_NAME = Map.of("plot", "土地认养", "animal", "家禽认养", "tree", "果树认养");

    /** 工作台曲线的演示底数（每天 ¥1,800–¥5,600），仅租户 2 有；叠加真实下单金额 */
    static long baselineGmv(long tenantId, long dayIndex) {
        if (tenantId != 2L) return 0L;
        return Math.round((3700 + Math.sin(dayIndex * 1.7) * 1900) / 10) * 1000L;
    }

    private static long epochMs(LocalDate d) {
        return d.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    private static long toMs(LocalDateTime t) {
        return t.atZone(ZONE).toInstant().toEpochMilli();
    }

    private static LocalDateTime fromMs(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> summary(Long tenantId) {
        LocalDate today = LocalDate.now(ZONE);
        long startToday = epochMs(today);
        LocalDate monthFirst = today.withDayOfMonth(1);
        long monthStart = epochMs(monthFirst);
        long now = System.currentTimeMillis();

        // 已支付认养订单全取（复购率、客单价要用全量）；农资订单（创建即计入）只取近 30 天和本月月初两者更早的起点之后，在内存里按天累加
        LocalDateTime earliest = fromMs(Math.min(monthStart, startToday - 29 * DAY));
        List<FarmOrderDO> paid = orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>()
                .isNotNull(FarmOrderDO::getPayTime));
        List<FarmSupplyOrderDO> supplyPaid = supplyOrderMapper.selectList(new LambdaQueryWrapper<FarmSupplyOrderDO>()
                .ge(FarmSupplyOrderDO::getCreateTime, earliest));

        // 近 30 天曲线（原型 08 dash）
        List<Map<String, Object>> gmvTrend = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate d = today.minusDays(29 - i);
            long dayIndex = Math.floorDiv(epochMs(d), DAY);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
            m.put("amount", baselineGmv(tenantId, dayIndex) + incomeOn(paid, supplyPaid, d));
            gmvTrend.add(m);
        }
        Map<String, Object> gmvPeak = gmvTrend.get(0);
        for (Map<String, Object> x : gmvTrend) {
            if ((Long) x.get("amount") > (Long) gmvPeak.get("amount")) gmvPeak = x;
        }
        long gmvToday = (Long) gmvTrend.get(29).get("amount");
        long gmvYesterday = (Long) gmvTrend.get(28).get("amount");
        long gmvMonth = 0;
        for (LocalDate d = monthFirst; !d.isAfter(today); d = d.plusDays(1)) {
            gmvMonth += baselineGmv(tenantId, Math.floorDiv(epochMs(d), DAY)) + incomeOn(paid, supplyPaid, d);
        }
        long orderCountMonth = paid.stream().filter(o -> toMs(o.getPayTime()) >= monthStart).count();
        long orderCountToday = paidCountBetween(paid, startToday, startToday + DAY);
        long orderCountYesterday = paidCountBetween(paid, startToday - DAY, startToday);
        long ordersLast7d = paidCountBetween(paid, startToday - 6 * DAY, startToday + DAY);
        long ordersPrev7d = paidCountBetween(paid, startToday - 13 * DAY, startToday - 6 * DAY);

        List<FarmAssetDO> assets = assetMapper.selectList(new LambdaQueryWrapper<FarmAssetDO>().ne(FarmAssetDO::getStatus, 20));
        List<FarmAdoptionDO> adoptions = adoptionMapper.selectList(FarmAdoptionDO::getStatus, 10);
        long pendingRecordCount = recordMapper.selectCount(FarmRecordDO::getStatus, 0);
        long pendingServiceOrderCount = serviceOrderMapper.selectCount(FarmServiceOrderDO::getStatus, 0);
        long overdueSelfTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getOwner, "member").eq(FarmTaskDO::getStatus, 0)
                .lt(FarmTaskDO::getDeadline, fromMs(now)));
        long unshipped = harvestMapper.selectCount(new LambdaQueryWrapper<FarmHarvestDO>().isNull(FarmHarvestDO::getShipmentId));
        long untraced = supplyOrderMapper.selectCount(FarmSupplyOrderDO::getTraceState, 0);

        Map<String, Object> risk = riskService.selfCheck();
        long riskBadCount = 0;
        for (Map<String, Object> it : (List<Map<String, Object>>) risk.get("items")) {
            if (!"ok".equals(it.get("status"))) riskBadCount++;
        }

        // 在养认养人、复购率：已支付订单 >=2 笔的认养人 / 有已支付订单的认养人
        long adoptingMemberCount = adoptions.stream().map(FarmAdoptionDO::getMemberId).filter(Objects::nonNull).distinct().count();
        Map<Long, Long> orderCountByMember = paid.stream().filter(o -> o.getMemberId() != null)
                .collect(Collectors.groupingBy(FarmOrderDO::getMemberId, Collectors.counting()));
        long repeatPayers = orderCountByMember.values().stream().filter(n -> n >= 2).count();
        double repurchaseRate = orderCountByMember.isEmpty() ? 0 : Math.round(repeatPayers * 1000.0 / orderCountByMember.size()) / 10.0;
        long avgOrderAmount = paid.isEmpty() ? 0 : Math.round(paid.stream().mapToLong(o -> o.getAmount() == null ? 0L : o.getAmount()).sum() / (double) paid.size());

        List<Map<String, Object>> careModeShare = new ArrayList<>();
        String[][] modes = {{"full", "全托管"}, {"semi", "半托管"}, {"self", "不托管"}};
        for (String[] mode : modes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mode", mode[0]);
            m.put("name", mode[1]);
            m.put("count", adoptions.stream().filter(a -> mode[0].equals(a.getCareMode())).count());
            careModeShare.add(m);
        }

        // 认养位使用情况（按资产类型）
        String[][] types = {{"plot", "位"}, {"animal", "只"}, {"tree", "棵"}};
        List<Map<String, Object>> slotUsage = new ArrayList<>();
        for (String[] type : types) {
            List<FarmAssetDO> list = assets.stream().filter(a -> type[0].equals(a.getAssetType())).collect(Collectors.toList());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type[0]);
            m.put("name", CATEGORY_NAME.get(type[0]));
            m.put("unit", type[1]);
            m.put("used", list.stream().filter(a -> a.getStatus() != null && a.getStatus() == 10).count());
            m.put("total", (long) list.size());
            slotUsage.add(m);
        }

        // 本周经营概览：近 7 天已支付订单按商品分类计数
        long weekFrom = startToday - 6 * DAY;
        List<FarmOrderDO> weekPaid = paid.stream().filter(o -> toMs(o.getPayTime()) >= weekFrom).collect(Collectors.toList());
        Map<Long, FarmProductDO> weekProducts = lookup.products(weekPaid.stream().map(FarmOrderDO::getProductId).collect(Collectors.toList()));
        List<Map<String, Object>> weekCategories = new ArrayList<>();
        for (String[] type : types) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", CATEGORY_NAME.get(type[0]));
            m.put("count", weekPaid.stream().filter(o -> {
                FarmProductDO p = o.getProductId() == null ? null : weekProducts.get(o.getProductId());
                return p != null && type[0].equals(p.getAssetType());
            }).count());
            weekCategories.add(m);
        }
        Map<String, Object> weekOverview = new LinkedHashMap<>();
        weekOverview.put("categories", weekCategories);
        weekOverview.put("avgOrderAmount", avgOrderAmount);
        weekOverview.put("repurchaseRate", repurchaseRate);
        weekOverview.put("refundRate", null);

        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(todo("record", "农事记录待审核", pendingRecordCount, "管家上传记录需复核后发布到 C 端", "warning", "/farm-work/record"));
        todos.add(todo("service", "代管工单待接单", pendingServiceOrderCount, "工单池里还没有管家接单", "warning", "/farm-supply/service-order"));
        todos.add(todo("harvest", "收成待发货", unshipped, "已录入收成但还没生成配送单", "primary", "/farm-order/shipment"));
        todos.add(todo("trace", "农资待登记施用", untraced, "自购农资未登记批次和施用时间，地块档案会标「待补充」", "warning", "/farm-supply/supply-order"));
        todos.add(todo("risk", "风险自检待改进", riskBadCount, "合规自检有未达标项，建议尽快处理", "danger", "/farm-compliance/self-check"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gmvToday", gmvToday);
        out.put("gmvYesterday", gmvYesterday);
        out.put("gmvDayOnDayRate", gmvYesterday == 0 ? null : (Object) (Math.round((gmvToday - gmvYesterday) * 1000.0 / gmvYesterday) / 10.0));
        out.put("gmvMonth", gmvMonth);
        out.put("gmvPeak", gmvPeak);
        out.put("orderCountToday", orderCountToday);
        out.put("orderCountYesterday", orderCountYesterday);
        out.put("orderCountMonth", orderCountMonth);
        out.put("orderCountLast7d", ordersLast7d);
        out.put("orderCountLast7dRate", ordersPrev7d == 0 ? null : (Object) (Math.round((ordersLast7d - ordersPrev7d) * 1000.0 / ordersPrev7d) / 10.0));
        out.put("pendingShipCount", unshipped);
        out.put("adoptingCount", adoptions.size());
        out.put("adoptingMemberCount", adoptingMemberCount);
        out.put("repurchaseRate", repurchaseRate);
        out.put("assetTotal", assets.size());
        out.put("assetAdopted", assets.stream().filter(a -> a.getStatus() != null && a.getStatus() == 10).count());
        out.put("pendingRecordCount", pendingRecordCount);
        out.put("pendingServiceOrderCount", pendingServiceOrderCount);
        out.put("overdueSelfTaskCount", overdueSelfTaskCount);
        out.put("riskScore", risk.get("score"));
        out.put("gmvTrend", gmvTrend);
        out.put("careModeShare", careModeShare);
        out.put("slotUsage", slotUsage);
        out.put("latestOrders", latestOrders());
        out.put("weekOverview", weekOverview);
        out.put("todos", todos);
        out.put("todayRecords", todayRecords(fromMs(startToday - DAY)));
        out.put("demo", true);
        return out;
    }

    private static long paidCountBetween(List<FarmOrderDO> paid, long from, long to) {
        return paid.stream().filter(o -> {
            long t = toMs(o.getPayTime());
            return t >= from && t < to;
        }).count();
    }

    /** 最新 5 笔认养订单（含未付款）：id, no, memberName, productTitle, amount, status, createTime */
    private List<Map<String, Object>> latestOrders() {
        List<FarmOrderDO> rows = orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>()
                .orderByDesc(FarmOrderDO::getCreateTime).orderByDesc(FarmOrderDO::getId).last("LIMIT 5"));
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        Map<Long, FarmMemberDO> members = lookup.members(rows.stream().map(FarmOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, FarmProductDO> products = lookup.products(rows.stream().map(FarmOrderDO::getProductId).collect(Collectors.toList()));
        for (FarmOrderDO o : rows) {
            FarmMemberDO m = o.getMemberId() == null ? null : members.get(o.getMemberId());
            FarmProductDO p = o.getProductId() == null ? null : products.get(o.getProductId());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", o.getId());
            v.put("no", o.getNo());
            v.put("memberName", m == null || m.getNickname() == null ? "" : m.getNickname());
            v.put("productTitle", p == null || p.getTitle() == null ? "" : p.getTitle());
            v.put("amount", o.getAmount());
            v.put("status", o.getStatus());
            v.put("createTime", o.getCreateTime());
            out.add(v);
        }
        return out;
    }

    /** 某天（本地日期）的收入：已支付认养订单 + 农资订单 */
    private static long incomeOn(List<FarmOrderDO> paid, List<FarmSupplyOrderDO> supplyPaid, LocalDate day) {
        long from = epochMs(day);
        long to = from + DAY;
        long sum = 0;
        for (FarmOrderDO o : paid) {
            long t = toMs(o.getPayTime());
            if (t >= from && t < to) sum += o.getAmount() == null ? 0L : o.getAmount();
        }
        for (FarmSupplyOrderDO o : supplyPaid) {
            long t = toMs(o.getCreateTime());
            if (t >= from && t < to) sum += o.getAmount() == null ? 0L : o.getAmount();
        }
        return sum;
    }

    private static Map<String, Object> todo(String type, String title, long count, String desc, String level, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("title", title);
        m.put("count", count);
        m.put("desc", desc);
        m.put("level", level);
        m.put("path", path);
        return m;
    }

    private List<FarmRecordViewVO> todayRecords(LocalDateTime since) {
        List<FarmRecordDO> rows = recordMapper.selectList(new LambdaQueryWrapper<FarmRecordDO>()
                .ge(FarmRecordDO::getCreateTime, since)
                .orderByDesc(FarmRecordDO::getCreateTime).orderByDesc(FarmRecordDO::getId)
                .last("LIMIT 6"));
        List<FarmRecordViewVO> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        Map<Long, FarmAssetDO> assetMap = byId(assetMapper.selectBatchIds(
                rows.stream().map(FarmRecordDO::getAssetId).filter(Objects::nonNull).collect(Collectors.toSet())), FarmAssetDO::getId);
        Map<Long, FarmAdoptionDO> adoptionMap = byId(adoptionMapper.selectBatchIds(
                rows.stream().map(FarmRecordDO::getAdoptionId).filter(Objects::nonNull).collect(Collectors.toSet())), FarmAdoptionDO::getId);
        Set<Long> memberIds = adoptionMap.values().stream().map(FarmAdoptionDO::getMemberId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FarmMemberDO> memberMap = memberIds.isEmpty() ? Collections.<Long, FarmMemberDO>emptyMap()
                : byId(memberMapper.selectBatchIds(memberIds), FarmMemberDO::getId);
        Set<Long> stewardIds = rows.stream().map(FarmRecordDO::getStewardId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = stewardIds.isEmpty() ? Collections.<Long, AdminUserRespDTO>emptyMap()
                : adminUserApi.getUserMap(stewardIds);

        for (FarmRecordDO r : rows) {
            FarmRecordViewVO v = BeanUtils.toBean(r, FarmRecordViewVO.class);
            FarmAssetDO a = r.getAssetId() == null ? null : assetMap.get(r.getAssetId());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            FarmAdoptionDO ad = r.getAdoptionId() == null ? null : adoptionMap.get(r.getAdoptionId());
            FarmMemberDO m = ad == null || ad.getMemberId() == null ? null : memberMap.get(ad.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            AdminUserRespDTO u = r.getStewardId() == null ? null : userMap.get(r.getStewardId());
            v.setStewardName(u == null || u.getNickname() == null ? "" : u.getNickname());
            out.add(v);
        }
        return out;
    }

    private static <T> Map<Long, T> byId(Collection<T> list, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>();
        for (T t : list) m.put(idFn.apply(t), t);
        return m;
    }
}
