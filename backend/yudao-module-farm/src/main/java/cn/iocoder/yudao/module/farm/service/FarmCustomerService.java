package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.customer.vo.FarmCustomerPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.customer.vo.FarmCustomerRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 客户管理 / 客户分层（mock：routes/farm-people.ts，口径见该文件顶部注释，两边必须一致）。
 * 全部只读，由 Member + Order + Adoption 派生，不新增表。金额单位：分。
 */
@Service
public class FarmCustomerService {

    private static final long DAY = 24L * 3600_000;
    private static final Set<Integer> PAID = Set.of(10, 20, 40);

    /** 分层定义：key, 名称, 级别, 级别配色, 判定规则, 建议动作（与 mock TIERS 一致） */
    static final String[][] TIERS = {
            {"core", "核心客户", "高", "success", "已付订单 ≥ 3 单，或近 365 天消费 ≥ ¥8,000", "专属管家 + 新品内测 + 生日礼盒"},
            {"growth", "成长客户", "中高", "warning", "已付订单 2 单，或近 365 天消费 ¥3,000–8,000", "续费提醒 + 亲子活动优先"},
            {"new", "新客", "待激活", "info", "首单距今 ≤ 90 天", "首季回访 + 到访邀约 + 农事科普"},
            {"sleep", "沉睡客户", "流失风险", "danger", "180 天无下单/支付动作", "电话回访 + 到园体验邀请"},
            {"other", "其他", "—", "info", "无已付订单，或不满足以上规则（原型未覆盖，演示补充）", "—"}
    };

    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;

    /** 内部行：比对外 VO 多几个只用于筛选和汇总的字段 */
    static class Row {
        Long id;
        String name;
        String mobileFull;
        String city;
        String level;
        int adoptCount;
        long totalAmount;
        List<String> tags = new ArrayList<>();
        LocalDateTime lastActive;
        LocalDateTime firstPaid;
        String tier;
        int activeAdoptionCount;
        int expiringCount;
        long expiringAmount;
        Set<Long> productIds = new LinkedHashSet<>();
        boolean hasFull;
    }

    // ---------- 口径函数（与 mock 一一对应） ----------

    static String cityOf(String addr) {
        if (StrUtil.isEmpty(addr)) return "";
        String s = addr.replaceFirst("^[^省]*?(省|自治区)", "");
        Matcher m = Pattern.compile("^(.+?)市").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    static String levelOf(long totalFen) {
        if (totalFen < 100_000) return "V1";
        if (totalFen < 300_000) return "V2";
        if (totalFen < 800_000) return "V3";
        return "V4";
    }

    static String maskMobile(String m) {
        return m == null ? "" : m.replaceFirst("^(\\d{3})\\d{4}(\\d{4})$", "$1****$2");
    }

    private static String tierOf(int paidCount, long spend365, LocalDateTime firstPaid, LocalDateTime lastActive, LocalDateTime now) {
        if (paidCount == 0) return "other";
        if (lastActive != null && ms(now) - ms(lastActive) > 180 * DAY) return "sleep";
        if (paidCount >= 3 || spend365 >= 800_000) return "core";
        if (paidCount == 2 || spend365 >= 300_000) return "growth";
        if (firstPaid != null && ms(now) - ms(firstPaid) <= 90 * DAY) return "new";
        return "other";
    }

    private static String renewOf(String tier) {
        return switch (tier) {
            case "core" -> "高";
            case "growth" -> "中";
            case "new" -> "新客";
            case "sleep" -> "低";
            default -> "—";
        };
    }

    private static String tierName(String key) {
        for (String[] t : TIERS) if (t[0].equals(key)) return t[1];
        return "";
    }

    private static long ms(LocalDateTime t) {
        return FarmAppSupport.toMillis(t);
    }

    List<Row> build(LocalDateTime now) {
        List<FarmMemberDO> members = memberMapper.selectList(new LambdaQueryWrapper<>());
        List<FarmOrderDO> allOrders = orderMapper.selectList(new LambdaQueryWrapper<>());
        List<FarmAdoptionDO> activeAds = adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>().eq(FarmAdoptionDO::getStatus, 10));
        Map<Long, FarmOrderDO> orderById = allOrders.stream().collect(Collectors.toMap(FarmOrderDO::getId, o -> o, (a, b) -> a));
        List<Row> out = new ArrayList<>();
        for (FarmMemberDO m : members) {
            List<FarmOrderDO> orders = allOrders.stream().filter(o -> m.getId().equals(o.getMemberId())).collect(Collectors.toList());
            List<FarmOrderDO> paid = orders.stream()
                    .filter(o -> o.getPayTime() != null && o.getStatus() != null && PAID.contains(o.getStatus())).collect(Collectors.toList());
            Row r = new Row();
            r.id = m.getId();
            r.name = m.getNickname();
            r.mobileFull = m.getMobile() == null ? "" : m.getMobile();
            r.city = cityOf(m.getAddress());
            r.adoptCount = paid.size();
            r.totalAmount = paid.stream().mapToLong(o -> o.getAmount() == null ? 0 : o.getAmount()).sum();
            long spend365 = paid.stream().filter(o -> ms(o.getPayTime()) >= ms(now) - 365 * DAY)
                    .mapToLong(o -> o.getAmount() == null ? 0 : o.getAmount()).sum();
            r.firstPaid = paid.stream().map(FarmOrderDO::getPayTime).min(Comparator.naturalOrder()).orElse(null);
            r.lastActive = orders.stream().flatMap(o -> java.util.stream.Stream.of(o.getCreateTime(), o.getPayTime()))
                    .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            r.level = levelOf(r.totalAmount);
            r.tier = tierOf(paid.size(), spend365, r.firstPaid, r.lastActive, now);
            if (paid.size() == 1 && r.firstPaid != null && ms(now) - ms(r.firstPaid) <= 90 * DAY) r.tags.add("新客");
            if (paid.size() >= 2) r.tags.add("多单客户");
            r.hasFull = paid.stream().anyMatch(o -> "full".equals(o.getCareMode()));
            if (r.hasFull) r.tags.add("全托管用户");
            if (orders.stream().anyMatch(o -> o.getStatus() != null && o.getStatus() == 50)) r.tags.add("有退款记录");
            for (FarmOrderDO o : paid) r.productIds.add(o.getProductId());
            for (FarmAdoptionDO a : activeAds) {
                if (!m.getId().equals(a.getMemberId())) continue;
                r.activeAdoptionCount++;
                if (a.getEndAt() != null && !a.getEndAt().isBefore(now) && ms(a.getEndAt()) - ms(now) <= 30 * DAY) {
                    r.expiringCount++;
                    FarmOrderDO ao = orderById.get(a.getOrderId());
                    r.expiringAmount += ao == null || ao.getAmount() == null ? 0 : ao.getAmount();
                }
            }
            out.add(r);
        }
        return out;
    }

    private static FarmCustomerRespVO toVO(Row r) {
        FarmCustomerRespVO v = new FarmCustomerRespVO();
        v.setId(r.id);
        v.setName(r.name);
        v.setMobile(maskMobile(r.mobileFull));
        v.setCity(r.city);
        v.setLevel(r.level);
        v.setAdoptCount(r.adoptCount);
        v.setTotalAmount(r.totalAmount);
        v.setTags(r.tags);
        v.setLastActive(r.lastActive);
        v.setTier(r.tier);
        v.setTierName(tierName(r.tier));
        v.setRenewIntent(renewOf(r.tier));
        v.setActiveAdoptionCount(r.activeAdoptionCount);
        v.setExpiringCount(r.expiringCount);
        return v;
    }

    // ---------- 接口 ----------

    public PageResult<FarmCustomerRespVO> page(FarmCustomerPageReqVO req) {
        LocalDateTime now = LocalDateTime.now();
        List<Row> rows = build(now).stream()
                .filter(c -> StrUtil.isEmpty(req.getKeyword()) || (c.name != null && c.name.contains(req.getKeyword())) || c.mobileFull.contains(req.getKeyword()))
                .filter(c -> StrUtil.isEmpty(req.getCity()) || c.city.equals(req.getCity()))
                .filter(c -> StrUtil.isEmpty(req.getTag()) || c.tags.contains(req.getTag()))
                .filter(c -> StrUtil.isEmpty(req.getTier()) || c.tier.equals(req.getTier()))
                .filter(c -> req.getProductId() == null || c.productIds.contains(req.getProductId()))
                .filter(c -> req.getActiveDays() == null || (c.lastActive != null && ms(now) - ms(c.lastActive) <= req.getActiveDays() * DAY))
                .filter(c -> req.getExpiring() == null || c.expiringCount > 0)
                .filter(c -> req.getMinAmount() == null || c.totalAmount >= req.getMinAmount())
                .filter(c -> req.getMaxAmount() == null || c.totalAmount <= req.getMaxAmount())
                .sorted((a, b) -> Long.compare(b.lastActive == null ? 0 : ms(b.lastActive), a.lastActive == null ? 0 : ms(a.lastActive)))
                .collect(Collectors.toList());
        int size = req.getPageSize() == null || req.getPageSize() <= 0 ? Math.max(rows.size(), 1) : Math.min(200, req.getPageSize());
        int no = Math.max(1, req.getPageNo() == null ? 1 : req.getPageNo());
        int from = Math.min(rows.size(), (no - 1) * size);
        int to = Math.min(rows.size(), from + size);
        return new PageResult<>(rows.subList(from, to).stream().map(FarmCustomerService::toVO).collect(Collectors.toList()), (long) rows.size());
    }

    public Map<String, Object> stats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime m0 = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        List<Row> rows = build(now);
        List<Row> buyers = rows.stream().filter(c -> c.adoptCount >= 1).collect(Collectors.toList());
        long orderCount = rows.stream().mapToLong(c -> c.adoptCount).sum();
        long totalAmount = rows.stream().mapToLong(c -> c.totalAmount).sum();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("newThisMonth", rows.stream().filter(c -> c.firstPaid != null && !c.firstPaid.isBefore(m0)).count());
        out.put("adopting", rows.stream().filter(c -> c.activeAdoptionCount > 0).count());
        out.put("expiring30", rows.stream().filter(c -> c.expiringCount > 0).count());
        out.put("repeatRate", buyers.isEmpty() ? null
                : Math.round((double) buyers.stream().filter(c -> c.adoptCount >= 2).count() / buyers.size() * 1000) / 10.0);
        out.put("avgOrderAmount", orderCount > 0 ? Math.round((double) totalAmount / orderCount) : 0L);
        out.put("cities", new ArrayList<>(rows.stream().map(c -> c.city).filter(StrUtil::isNotEmpty).collect(Collectors.toCollection(TreeSet::new))));
        out.put("tags", List.of("新客", "多单客户", "全托管用户", "有退款记录"));
        return out;
    }

    public Map<String, Object> tierSummary() {
        LocalDateTime now = LocalDateTime.now();
        List<Row> rows = build(now);
        List<Row> expiring = rows.stream().filter(c -> c.expiringCount > 0).collect(Collectors.toList());

        List<Map<String, Object>> tiers = new ArrayList<>();
        for (String[] t : TIERS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", t[0]);
            m.put("name", t[1]);
            m.put("level", t[2]);
            m.put("levelType", t[3]);
            m.put("rule", t[4]);
            m.put("action", t[5]);
            m.put("count", rows.stream().filter(c -> c.tier.equals(t[0])).count());
            tiers.add(m);
        }
        Map<String, Object> exp = new LinkedHashMap<>();
        exp.put("customerCount", expiring.size());
        exp.put("adoptionCount", expiring.stream().mapToInt(c -> c.expiringCount).sum());
        exp.put("estimateAmount", expiring.stream().mapToLong(c -> c.expiringAmount).sum());
        exp.put("remindDays", List.of(30, 15, 3));

        List<Map<String, Object>> packs = new ArrayList<>();
        packs.add(pack("高价值待续费", expiring.size(), "到期规则自动生成：认养中且 30 天内到期", null, "expiring"));
        packs.add(pack("核心客户", rows.stream().filter(c -> c.tier.equals("core")).count(), "分层规则：已付订单 ≥ 3 或近 365 天消费 ≥ ¥8,000", "core", "tier"));
        packs.add(pack("沉睡客户召回", rows.stream().filter(c -> c.tier.equals("sleep")).count(), "分层规则：180 天无下单/支付动作", "sleep", "tier"));
        packs.add(pack("全托管用户", rows.stream().filter(c -> c.hasFull).count(), "标签组合：有全托管已付订单", null, "tag"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("tiers", tiers);
        out.put("expiring", exp);
        out.put("packs", packs);
        out.put("updatedAt", ms(now));
        return out;
    }

    private static Map<String, Object> pack(String name, long count, String buildWay, String tier, String query) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("count", count);
        m.put("buildWay", buildWay);
        m.put("tier", tier);
        m.put("query", query);
        return m;
    }
}
