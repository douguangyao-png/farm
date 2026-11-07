package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.m;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.r1;

/**
 * 交易流水、分账结算（第二期 B；mock：mock-server/src/farm/ext-b.ts 的 flowRows / flowStats / splitView）。
 * 流水由认养订单、农资订单、已完成的代管工单派生，服务费率 3%、T+1 结算是演示口径，不是真实支付流水。
 * 金额单位：分。
 */
@Service
public class FarmFinanceService {

    public static final double FEE_RATE = 0.03;
    private static final long DAY = 86400_000L;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Map<String, String> TYPE_NAME = new LinkedHashMap<>();

    static {
        TYPE_NAME.put("adopt", "认养支付");
        TYPE_NAME.put("supply", "农资购买");
        TYPE_NAME.put("service", "代管服务");
    }

    /** 默认分账规则（演示）：经营主体 88 / 平台 3 / 土地流转方 6 / 渠道 3 */
    private static final String[][] SPLIT_RULES = {
            {"operator", "", "经营主体", "88"},
            {"platform", "平台技术服务费", "平台", "3"},
            {"land", "土地流转方（村集体）", "合作方", "6"},
            {"channel", "渠道推广（小程序）", "渠道", "3"}};

    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmProductMapper productMapper;
    @Autowired
    private TenantService tenantService;

    /** 一行流水 */
    public static class Flow {
        public String key, tradeNo, memberName, type, typeName, settleStatus, settleStatusName, refNo;
        public Long memberId;
        public long amount, fee, net, time;

        Map<String, Object> toMap() {
            return m("key", key, "tradeNo", tradeNo, "memberId", memberId, "memberName", memberName, "type", type,
                    "typeName", typeName, "amount", amount, "fee", fee, "net", net, "settleStatus", settleStatus,
                    "settleStatusName", settleStatusName, "time", time, "refNo", refNo);
        }
    }

    static long ms(LocalDateTime t) {
        return t.atZone(ZONE).toInstant().toEpochMilli();
    }

    static long startOfDay(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    /** 全部流水（时间倒序） */
    public List<Flow> flows() {
        Map<Long, String> nick = memberMapper.selectList().stream()
                .collect(Collectors.toMap(FarmMemberDO::getId, x -> x.getNickname() == null ? "" : x.getNickname(), (a, b) -> a));
        List<Object[]> raw = new ArrayList<>(); // key, memberId, type, amount, time, refNo
        for (FarmOrderDO o : orderMapper.selectList()) {
            if (o.getPayTime() != null && (o.getStatus() == null || o.getStatus() != 30)) {
                raw.add(new Object[]{"o" + o.getId(), o.getMemberId(), "adopt", o.getAmount(), ms(o.getPayTime()), o.getNo()});
            }
        }
        for (FarmSupplyOrderDO s : supplyOrderMapper.selectList()) {
            raw.add(new Object[]{"s" + s.getId(), s.getMemberId(), "supply", s.getAmount(), ms(s.getCreateTime()), s.getNo()});
        }
        for (FarmServiceOrderDO s : serviceOrderMapper.selectList()) {
            if (s.getStatus() != null && s.getStatus() == 20 && s.getPrice() != null && s.getPrice() > 0) {
                LocalDateTime t = s.getFinishTime() != null ? s.getFinishTime() : s.getCreateTime();
                raw.add(new Object[]{"w" + s.getId(), s.getMemberId(), "service", s.getPrice(), ms(t), s.getNo()});
            }
        }
        raw.sort(Comparator.<Object[], Long>comparing(r -> (Long) r[4]).thenComparing(r -> (String) r[0]));
        Map<String, Integer> seq = new HashMap<>();
        long now = System.currentTimeMillis();
        List<Flow> out = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.BASIC_ISO_DATE;
        for (Object[] r : raw) {
            long time = (Long) r[4];
            String day = Instant.ofEpochMilli(time).atZone(ZONE).toLocalDate().format(f);
            int n = seq.merge(day, 1, Integer::sum);
            Flow x = new Flow();
            x.key = (String) r[0];
            x.tradeNo = "T" + day + String.format("%03d", n);
            x.memberId = (Long) r[1];
            x.memberName = nick.getOrDefault(x.memberId, "");
            x.type = (String) r[2];
            x.typeName = TYPE_NAME.get(x.type);
            x.amount = (Long) r[3];
            x.fee = Math.round(x.amount * FEE_RATE);
            x.net = x.amount - x.fee;
            boolean settled = now - time >= DAY;
            x.settleStatus = settled ? "settled" : "settling";
            x.settleStatusName = settled ? "已结算" : "结算中";
            x.time = time;
            x.refNo = (String) r[5];
            out.add(x);
        }
        out.sort(Comparator.comparingLong((Flow x) -> x.time).reversed());
        return out;
    }

    public FarmFulfillPage<Map<String, Object>> flowPage(PageParam req, String tradeNo, String memberName, String type,
                                                          String settleStatus, Long beginTime, Long endTime) {
        List<Flow> all = flows();
        List<Map<String, Object>> rows = all.stream()
                .filter(r -> tradeNo == null || tradeNo.isEmpty() || r.tradeNo.contains(tradeNo))
                .filter(r -> memberName == null || memberName.isEmpty() || r.memberName.contains(memberName))
                .filter(r -> type == null || type.isEmpty() || r.type.equals(type))
                .filter(r -> settleStatus == null || settleStatus.isEmpty() || r.settleStatus.equals(settleStatus))
                .filter(r -> beginTime == null || r.time >= beginTime)
                .filter(r -> endTime == null || r.time <= endTime)
                .map(Flow::toMap).collect(Collectors.toList());
        Map<String, Object> stats = flowStats(all);
        return new FarmFulfillPage<>(rows, req, stats);
    }

    public Map<String, Object> flowStats(List<Flow> rows) {
        long now = System.currentTimeMillis();
        long d0 = startOfDay(now);
        long w1 = now - 30 * DAY;
        long w0 = now - 60 * DAY;
        List<Flow> today = rows.stream().filter(r -> r.time >= d0).collect(Collectors.toList());
        List<Flow> cur = rows.stream().filter(r -> r.time >= w1).collect(Collectors.toList());
        long prevAmt = rows.stream().filter(r -> r.time >= w0 && r.time < w1).mapToLong(r -> r.amount).sum();
        long curAmt = cur.stream().mapToLong(r -> r.amount).sum();
        List<Flow> settling = rows.stream().filter(r -> "settling".equals(r.settleStatus)).collect(Collectors.toList());
        return m("todayAmount", today.stream().mapToLong(r -> r.amount).sum(), "todayCount", today.size(),
                "last30Amount", curAmt, "last30Count", cur.size(),
                "last30Growth", prevAmt > 0 ? r1((curAmt - prevAmt) * 100.0 / prevAmt) : null,
                "last30Fee", cur.stream().mapToLong(r -> r.fee).sum(), "feeRate", FEE_RATE * 100,
                "pendingAmount", settling.stream().mapToLong(r -> r.net).sum(), "pendingCount", settling.size());
    }

    /** 按比例拆分，尾差给经营主体，合计恒等于总额 */
    private static long[] splitAmount(long total) {
        long[] parts = new long[SPLIT_RULES.length];
        long sum = 0;
        for (int i = 0; i < parts.length; i++) {
            parts[i] = Math.round(total * Integer.parseInt(SPLIT_RULES[i][3]) / 100.0);
            sum += parts[i];
        }
        parts[0] += total - sum;
        return parts;
    }

    public Map<String, Object> split() {
        TenantDO tenant = tenantService.getTenant(TenantContextHolder.getRequiredTenantId());
        String tenantName = tenant == null || tenant.getName() == null ? "" : tenant.getName();
        List<FarmProductDO> products = productMapper.selectList(new LambdaQueryWrapperX<FarmProductDO>().orderByAsc(FarmProductDO::getId));
        FarmProductDO sample = products.isEmpty() ? null : products.get(0);
        long example = sample != null && sample.getBasePrice() != null ? sample.getBasePrice() : 128000L;
        long[] ex = splitAmount(example);
        List<Map<String, Object>> rules = new ArrayList<>();
        for (int i = 0; i < SPLIT_RULES.length; i++) {
            String[] r = SPLIT_RULES[i];
            rules.add(m("key", r[0], "name", i == 0 ? tenantName : r[1], "type", r[2], "percent", Integer.parseInt(r[3]), "exampleAmount", ex[i]));
        }
        long now = System.currentTimeMillis();
        long total = flows().stream().filter(r -> r.time >= now - 30 * DAY).mapToLong(r -> r.amount).sum();
        long[] mp = splitAmount(total);
        String[] labels = {"经营者到账", "平台服务费", "土地流转分成", "渠道分成"};
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < SPLIT_RULES.length; i++) items.add(m("key", SPLIT_RULES[i][0], "label", labels[i], "amount", mp[i]));

        // 代管工单的分账比例（管家/农场/平台）是真实数据：近 30 天已完成工单
        List<FarmServiceOrderDO> svc = serviceOrderMapper.selectList().stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 20)
                .filter(s -> ms(s.getFinishTime() != null ? s.getFinishTime() : s.getCreateTime()) >= now - 30 * DAY)
                .collect(Collectors.toList());
        Map<String, Object> serviceSplit = m("count", svc.size(), "total", svc.stream().mapToLong(FarmServiceOrderDO::getPrice).sum(),
                "steward", svcSum(svc, "steward"), "farm", svcSum(svc, "farm"), "platform", svcSum(svc, "platform"));

        // 发票：演示。金额最高的 3 笔已支付认养订单，抬头用认养人昵称，状态按序固定
        Map<Long, String> nick = memberMapper.selectList().stream()
                .collect(Collectors.toMap(FarmMemberDO::getId, x -> x.getNickname() == null ? "" : x.getNickname(), (a, b) -> a));
        List<FarmOrderDO> paid = orderMapper.selectList().stream()
                .filter(o -> o.getPayTime() != null && (o.getStatus() == null || o.getStatus() != 30))
                .sorted(Comparator.comparingLong(FarmOrderDO::getAmount).reversed().thenComparing(FarmOrderDO::getId))
                .limit(3).collect(Collectors.toList());
        List<Map<String, Object>> invoices = new ArrayList<>();
        for (int i = 0; i < paid.size(); i++) {
            FarmOrderDO o = paid.get(i);
            invoices.add(m("id", o.getId(), "title", "个人（" + nick.getOrDefault(o.getMemberId(), "") + "）", "amount", o.getAmount(),
                    "kind", "增值税普票", "statusName", i == 2 ? "申请中" : "已开具", "orderNo", o.getNo()));
        }
        return m("demo", true, "sampleProductTitle", sample == null ? "" : sample.getTitle(), "exampleAmount", example, "rules", rules,
                "account", m("type", "对公银行账户", "name", tenantName, "bank", "（演示）中国农业银行 湖州分行", "cycle", "T+1（节假日顺延）",
                        "invoiceBy", "本平台代开 · 可下载电子发票", "verified", true),
                "month", m("label", "近 30 天", "total", total, "items", items), "serviceSplit", serviceSplit, "invoices", invoices,
                "feeRate", FEE_RATE * 100);
    }

    @SuppressWarnings("unchecked")
    private static long svcSum(List<FarmServiceOrderDO> svc, String k) {
        long s = 0;
        for (FarmServiceOrderDO x : svc) {
            Object pct = x.getSplit() == null ? null : x.getSplit().get(k);
            if (pct instanceof Number) s += Math.round(x.getPrice() * ((Number) pct).doubleValue() / 100.0);
        }
        return s;
    }

    /** 给其它服务（资金公示）用：近 30 天流水合计与服务费 */
    public long[] last30TotalAndFee() {
        long now = System.currentTimeMillis();
        long total = 0, fee = 0;
        for (Flow r : flows()) {
            if (r.time >= now - 30 * DAY) {
                total += r.amount;
                fee += r.fee;
            }
        }
        return new long[]{total, fee};
    }
}
