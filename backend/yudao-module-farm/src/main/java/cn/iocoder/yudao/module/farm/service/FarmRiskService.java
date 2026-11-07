package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmComplianceHitDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmComplianceHitMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 风险自检（契约 2.1；对应 mock-server/src/routes/farm-admin.ts 第 69–94 行） */
@Service
public class FarmRiskService {

    /** 与 mock-server/src/compliance.ts 的 SUGGESTION 一致 */
    public static final String SUGGESTION = "建议改为实物交付的说法，例如「约定交付 240 斤，不足部分按同等品质补发」";

    @Resource
    private FarmBaseMapper baseMapper;
    @Resource
    private FarmOrderMapper orderMapper;
    @Resource
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Resource
    private FarmComplianceHitMapper complianceHitMapper;

    public Map<String, Object> selfCheck() {
        List<FarmBaseDO> bases = baseMapper.selectList(new LambdaQueryWrapper<FarmBaseDO>());
        long certOk = bases.stream().filter(b -> b.getCertNo() != null && !b.getCertNo().isEmpty()).count();
        List<FarmOrderDO> paid = orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>().isNotNull(FarmOrderDO::getPayTime));
        long signed = paid.stream().filter(o -> Boolean.TRUE.equals(o.getContractAgreed())).count();
        List<FarmComplianceHitDO> hits = complianceHitMapper.selectList(new LambdaQueryWrapper<FarmComplianceHitDO>()
                .gt(FarmComplianceHitDO::getCreateTime, LocalDateTime.now().minusDays(30))
                .orderByDesc(FarmComplianceHitDO::getCreateTime).orderByDesc(FarmComplianceHitDO::getId));
        List<FarmSupplyOrderDO> so = supplyOrderMapper.selectList(new LambdaQueryWrapper<FarmSupplyOrderDO>());
        long traced = so.stream().filter(o -> o.getTraceState() != null && o.getTraceState() == 10).count();

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("cert", "资质证照", 20 * ratio(certOk, bases.size()),
                certOk + "/" + bases.size() + " 个基地已上传资质编号", "补齐基地的经营 / 防疫资质"));
        items.add(item("fund", "资金路径", 20, "认养款、农资款都走平台商户号，没有个人收款码", ""));
        items.add(item("contract", "合同签署率", 20 * ratio(signed, paid.size()),
                signed + "/" + paid.size() + " 笔已支付订单签了电子合同", "未签合同的订单请补签"));
        items.add(item("copy", "文案合规", Math.max(0, 20 - hits.size() * 4),
                hits.isEmpty() ? "近 30 天没有被拦截的文案" : "近 30 天有 " + hits.size() + " 次发布被拦截", SUGGESTION));
        items.add(item("trace", "溯源完整度", 20 * ratio(traced, so.size()),
                traced + "/" + so.size() + " 笔自购农资已登记批次和施用时间", "在「农资订单」里登记施用，否则地块档案会标「待补充」"));

        int score = 0;
        for (Map<String, Object> it : items) score += (Integer) it.get("score");

        List<Map<String, Object>> hitList = new ArrayList<>();
        for (FarmComplianceHitDO h : hits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("productId", h.getProductId());
            m.put("productTitle", h.getProductTitle());
            m.put("words", h.getWords());
            m.put("time", h.getCreateTime());
            hitList.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", score);
        out.put("items", items);
        out.put("hits", hitList);
        return out;
    }

    private static double ratio(long a, long b) {
        return b == 0 ? 1 : (double) a / b;
    }

    private static Map<String, Object> item(String key, String name, double score, String detail, String suggestion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("score", (int) Math.round(score));
        m.put("max", 20);
        m.put("status", score >= 18 ? "ok" : score >= 12 ? "warn" : "bad");
        m.put("detail", detail);
        m.put("suggestion", score >= 18 ? "" : suggestion);
        return m;
    }
}
