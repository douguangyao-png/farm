package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.m;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.r1;

/**
 * 经营分析（第二期 B；mock：farm/ext-b.ts 的 analysisView）。
 * 下单/支付/签约、品类 GMV、复购、记录覆盖、约定交付完成率来自业务表；曝光、浏览、渠道、续费等无埋点的指标是演示值，逐项标 demo。
 */
@Service
public class FarmAnalysisService {

    private static final long DAY = 86400_000L;
    private static final Map<String, String> CATEGORY_NAME = new HashMap<>();

    static {
        CATEGORY_NAME.put("plot", "土地认养");
        CATEGORY_NAME.put("animal", "家禽认养");
        CATEGORY_NAME.put("tree", "果树认养");
    }

    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmProductMapper productMapper;
    @Autowired
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmDeviceService deviceService;

    private static boolean paid(FarmOrderDO o) {
        return o.getPayTime() != null && (o.getStatus() == null || o.getStatus() != 30);
    }

    public Map<String, Object> summary(int daysParam) {
        int days = daysParam == 7 || daysParam == 90 ? daysParam : 30;
        long now = System.currentTimeMillis();
        long from = now - days * DAY;
        List<FarmOrderDO> orders = orderMapper.selectList();
        Map<Long, FarmProductDO> products = productMapper.selectList().stream().collect(Collectors.toMap(FarmProductDO::getId, p -> p, (a, b) -> a));
        List<FarmOrderDO> inWin = orders.stream().filter(o -> FarmFinanceService.ms(o.getCreateTime()) >= from).collect(Collectors.toList());
        List<FarmOrderDO> paidWin = inWin.stream().filter(FarmAnalysisService::paid).collect(Collectors.toList());
        List<FarmOrderDO> signed = paidWin.stream().filter(o -> Boolean.TRUE.equals(o.getContractAgreed())).collect(Collectors.toList());
        int placed = inWin.size();
        // 曝光、浏览没有埋点：按原型转化率（浏览→下单 24.6%，曝光→浏览 26.3%）由下单数反推，标演示
        long browse = placed > 0 ? Math.round(placed / 0.246) : 0;
        long exposure = browse > 0 ? Math.round(browse / 0.263) : 0;
        long renew = Math.round(signed.size() * 0.628);
        Object[][] st = {{"曝光次数", exposure, true}, {"商品浏览", browse, true}, {"进入下单", (long) placed, false},
                {"完成支付", (long) paidWin.size(), false}, {"签署合同", (long) signed.size(), false}, {"首季续费", renew, true}};
        List<Map<String, Object>> funnel = new ArrayList<>();
        for (int i = 0; i < st.length; i++) {
            long v = (Long) st[i][1];
            double rate = i == 0 ? 100 : (Long) st[i - 1][1] > 0 ? r1(v * 100.0 / (Long) st[i - 1][1]) : 0;
            funnel.add(m("name", st[i][0], "value", v, "demo", st[i][2], "rate", rate));
        }

        // 渠道来源：系统没有渠道字段，按固定占比拆分已支付订单数，标演示（最大余数法，合计等于订单数）
        Object[][] chShare = {{"小程序自然流量", 34}, {"短视频/小红书", 24}, {"老客推荐", 18}, {"到园转化", 11}, {"异业合作", 7}, {"朋友圈海报", 6}};
        long[] val = new long[chShare.length];
        double[] frac = new double[chShare.length];
        long left = paidWin.size();
        for (int i = 0; i < chShare.length; i++) {
            double exact = paidWin.size() * ((Integer) chShare[i][1]) / 100.0;
            val[i] = (long) Math.floor(exact);
            frac[i] = exact - val[i];
            left -= val[i];
        }
        Integer[] idx = new Integer[chShare.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, Comparator.comparingDouble((Integer i) -> frac[i]).reversed());
        for (Integer i : idx) {
            if (left <= 0) break;
            val[i]++;
            left--;
        }
        List<Map<String, Object>> channels = new ArrayList<>();
        for (int i = 0; i < chShare.length; i++) channels.add(m("name", chShare[i][0], "value", val[i], "demo", true));

        // 品类结构：真实 GMV
        Map<String, Long> cat = new LinkedHashMap<>();
        for (FarmOrderDO o : paidWin) {
            FarmProductDO p = products.get(o.getProductId());
            String name = p == null ? "其他" : CATEGORY_NAME.getOrDefault(p.getAssetType(), "其他");
            cat.merge(name, o.getAmount(), Long::sum);
        }
        long supplyGmv = supplyOrderMapper.selectList().stream().filter(s -> FarmFinanceService.ms(s.getCreateTime()) >= from).mapToLong(FarmSupplyOrderDO::getAmount).sum();
        long svcGmv = serviceOrderMapper.selectList().stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 20)
                .filter(s -> FarmFinanceService.ms(s.getFinishTime() != null ? s.getFinishTime() : s.getCreateTime()) >= from)
                .mapToLong(FarmServiceOrderDO::getPrice).sum();
        if (supplyGmv > 0) cat.put("农资商品", supplyGmv);
        if (svcGmv > 0) cat.put("代管服务", svcGmv);
        List<Map<String, Object>> categories = cat.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> m("name", e.getKey(), "value", e.getValue())).collect(Collectors.toList());

        // 复购与留存
        List<FarmOrderDO> allPaid = orders.stream().filter(FarmAnalysisService::paid).collect(Collectors.toList());
        Map<Long, Integer> perMember = new HashMap<>();
        for (FarmOrderDO o : allPaid) perMember.merge(o.getMemberId(), 1, Integer::sum);
        long repeat = perMember.values().stream().filter(n -> n >= 2).count();
        long cancelledPaid = orders.stream().filter(o -> o.getStatus() != null && o.getStatus() == 30 && o.getPayTime() != null).count();
        List<FarmAdoptionDO> ads = adoptionMapper.selectList();
        double avgYears = ads.isEmpty() ? 0 : r1(ads.stream().mapToLong(a -> FarmFinanceService.ms(a.getEndAt()) - FarmFinanceService.ms(a.getStartAt())).sum() / (double) ads.size() / (365 * DAY));
        List<Map<String, Object>> retention = new ArrayList<>();
        retention.add(metric("累计复购率", perMember.isEmpty() ? 0 : r1(repeat * 100.0 / perMember.size()), "%", false, "已支付 2 单及以上的认养人 / 已支付认养人"));
        retention.add(metric("年度续费率", 62.8, "%", true, "系统尚无续费数据"));
        retention.add(metric("平均认养周期", avgYears, "年", false, "认养起止时间平均"));
        retention.add(metric("退费率", allPaid.size() + cancelledPaid == 0 ? 0 : r1(cancelledPaid * 100.0 / (allPaid.size() + cancelledPaid)), "%", false,
                "已支付后取消的订单占比；退款闭环上线后以退款单为准"));
        retention.add(metric("投诉率", 0.4, "%", true, "系统尚无投诉分类"));

        // 履约质量
        List<FarmAdoptionDO> active = ads.stream().filter(a -> a.getStatus() != null && a.getStatus() == 10).collect(Collectors.toList());
        Set<Long> withRecord = new HashSet<>();
        for (FarmRecordDO r : recordMapper.selectList()) if (r.getStatus() != null && r.getStatus() == 10 && r.getAdoptionId() != null) withRecord.add(r.getAdoptionId());
        long covered = active.stream().filter(a -> withRecord.contains(a.getId())).count();
        double promised = 0, harvested = 0;
        for (FarmAdoptionDO a : ads) {
            if (a.getPromisedJin() == null) continue;
            double p = a.getPromisedJin().doubleValue();
            promised += p;
            harvested += Math.min(a.getHarvestedJin() == null ? 0 : a.getHarvestedJin().doubleValue(), p);
        }
        List<FarmDeviceService.Device> cams = deviceService.devices().stream().filter(x -> "camera".equals(x.kind)).collect(Collectors.toList());
        long camOnline = cams.stream().filter(x -> "online".equals(x.status)).count();
        List<Map<String, Object>> quality = new ArrayList<>();
        quality.add(metric("按时交付率", 98.6, "%", true, "系统尚无承诺送达时间"));
        quality.add(metric("农事记录覆盖率", active.isEmpty() ? 0 : r1(covered * 100.0 / active.size()), "%", false, "在养认养中至少有 1 条已发布记录的占比"));
        quality.add(metric("监控在线率", cams.isEmpty() ? 0 : r1(camOnline * 100.0 / cams.size()), "%", true, "设备状态为演示数据"));
        quality.add(metric("约定交付完成率", promised > 0 ? r1(harvested * 100.0 / promised) : 0, "%", false, "已收斤数 / 约定交付斤数（全托管）"));
        quality.add(metric("客服平均响应", 8, "分钟", true, "系统尚无客服数据"));

        return m("days", days, "funnel", funnel, "channels", channels, "categories", categories, "retention", retention, "quality", quality,
                "paidCount", paidWin.size());
    }

    private static Map<String, Object> metric(String label, Object value, String unit, boolean demo, String hint) {
        return m("label", label, "value", value, "unit", unit, "demo", demo, "hint", hint);
    }
}
