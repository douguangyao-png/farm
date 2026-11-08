package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.m;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 托管模式配置（第二期 B；mock：farm/ext-b.ts 的 hostingConfigView）。
 * 只读汇总：三档价格、产出承诺、合同版本、责任矩阵都取自认养商品的 care_modes（同一份口径，见 docs/10 第二节），
 * 保存走已有的 PUT /farm/product/update，不在这里另存一份。
 */
@Service
public class FarmHostingConfigService {

    private static final long DAY = 86400_000L;
    private static final List<String> MODES = Arrays.asList("full", "semi", "self");
    private static final Map<String, String> MODE_NAME = new LinkedHashMap<>();
    private static final List<String> CAT_ORDER = Arrays.asList("seed", "fert", "tool", "bio", "svc", "ins");

    static {
        MODE_NAME.put("full", "全托管");
        MODE_NAME.put("semi", "半托管");
        MODE_NAME.put("self", "不托管");
    }

    @Autowired
    private FarmProductMapper productMapper;
    @Autowired
    private FarmSupplyMapper supplyMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dutyOf(Map<String, Object> mode) {
        Object d = mode == null ? null : mode.get("duty");
        return d instanceof List ? (List<Map<String, Object>>) d : new ArrayList<>();
    }

    private static long num(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    public Map<String, Object> get(Long productId) {
        List<FarmProductDO> products = productMapper.selectList(new LambdaQueryWrapperX<FarmProductDO>().orderByAsc(FarmProductDO::getId));
        FarmProductDO p = products.stream().filter(x -> x.getId().equals(productId)).findFirst().orElse(products.isEmpty() ? null : products.get(0));
        if (p == null) return m("empty", true, "products", new ArrayList<>(), "product", null, "modes", new ArrayList<>());
        List<Map<String, Object>> careModes = p.getCareModes() == null ? new ArrayList<>() : p.getCareModes();
        Map<String, Object> fullCfg = careModes.stream().filter(x -> "full".equals(x.get("mode"))).findFirst().orElse(null);
        long fullPrice = fullCfg != null ? num(fullCfg.get("price")) : p.getBasePrice();
        List<FarmSupplyDO> supplies = supplyMapper.selectList(new LambdaQueryWrapperX<FarmSupplyDO>().orderByAsc(FarmSupplyDO::getId)).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 0 && !Boolean.FALSE.equals(s.getRetailAllowed())).collect(Collectors.toList());

        List<Map<String, Object>> modes = new ArrayList<>();
        for (String mode : MODES) {
            Map<String, Object> c = careModes.stream().filter(x -> mode.equals(x.get("mode"))).findFirst().orElse(null);
            List<Map<String, Object>> duty = dutyOf(c);
            long farm = duty.stream().filter(x -> "farm".equals(x.get("owner"))).count();
            List<Map<String, Object>> pkg = new ArrayList<>();
            if (!"full".equals(mode)) {
                List<FarmSupplyDO> fit = supplies.stream().filter(s -> s.getFitModes() != null && s.getFitModes().contains(mode))
                        .sorted(Comparator.comparingInt((FarmSupplyDO s) -> CAT_ORDER.indexOf(s.getCat())).thenComparing(FarmSupplyDO::getId))
                        .collect(Collectors.toList());
                for (int i = 0; i < fit.size(); i++) {
                    FarmSupplyDO s = fit.get(i);
                    pkg.add(m("id", s.getId(), "name", s.getName(), "cat", s.getCat(), "price", s.getPrice(), "unit", s.getUnit(), "recommended", i == 0));
                }
            }
            String contractVersion = c == null ? "" : str(c.get("contractVersion"));
            modes.add(m("mode", mode, "name", MODE_NAME.get(mode), "configured", c != null, "enabled", c != null && Boolean.TRUE.equals(c.get("enabled")),
                    "price", c == null ? 0 : num(c.get("price")), "priceDelta", c != null && !"full".equals(mode) ? num(c.get("price")) - fullPrice : null,
                    "slogan", c == null ? "" : str(c.get("slogan")), "outputPromise", c == null ? "" : str(c.get("outputPromise")),
                    "contractVersion", contractVersion,
                    "contractName", c == null ? "" : "《认养服务合同（" + MODE_NAME.get(mode) + "）》" + contractVersion,
                    "weeklyTodo", c == null ? 0 : num(c.get("weeklyTodo")), "insurance", c == null ? "" : str(c.get("insurance")),
                    "duty", duty, "dutyFarmCount", farm, "dutyMemberCount", duty.size() - farm, "supplyPackage", pkg));
        }

        List<String> items = new ArrayList<>();
        for (Map<String, Object> cm : careModes) for (Map<String, Object> d : dutyOf(cm)) if (!items.contains(str(d.get("item")))) items.add(str(d.get("item")));
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (String item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", item);
            for (String mode : MODES) {
                Map<String, Object> c = careModes.stream().filter(x -> mode.equals(x.get("mode"))).findFirst().orElse(null);
                row.put(mode, dutyOf(c).stream().filter(d -> item.equals(str(d.get("item")))).map(d -> d.get("owner")).findFirst().orElse(null));
            }
            matrix.add(row);
        }

        long now = System.currentTimeMillis();
        List<FarmServiceOrderDO> overdue = serviceOrderMapper.selectList().stream()
                .filter(s -> s.getFromTaskId() != null && s.getStatus() != null && s.getStatus() == 20)
                .filter(s -> FarmFinanceService.ms(s.getFinishTime() != null ? s.getFinishTime() : s.getCreateTime()) >= now - 30 * DAY)
                .collect(Collectors.toList());
        long pesticide = supplyMapper.selectList().stream().filter(s -> "pesticide".equals(s.getCat()) || Boolean.FALSE.equals(s.getRetailAllowed())).count();
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(m("k", "农药零售", "v", "化学农药不得对个人零售；生物防治产品仅可绑定「农场代施」工单", "tag", "已拦截"));
        rules.add(m("k", "投入品登记", "v", "认养人自购农资须登记批次与施用时间，统一进入溯源台账", "tag", "强制"));
        rules.add(m("k", "产出表述", "v", "文案禁止出现收益承诺类的金融词语，保存时自动拦截并给出改写建议", "tag", "强制"));

        return m("product", m("id", p.getId(), "title", p.getTitle(), "basePrice", p.getBasePrice()),
                "products", products.stream().map(x -> m("id", x.getId(), "title", x.getTitle())).collect(Collectors.toList()),
                "modes", modes, "dutyMatrix", matrix,
                "switchRule", "每周期可切换 1 次；升级补差价，降级按剩余周期折算退差额（扣已发生投入）；地块编号、摄像头绑定、历史档案连续保留",
                "trigger", m("sources", Arrays.asList("作物生长日历", "IoT 阈值", "管家手动"), "example", "土壤墒情 < 22% 且 48h 无降雨 → 生成浇水任务",
                        "remind", "任务前 1 天 + 当天 9:00 双提醒", "overdue", "逾期 48h 自动推送「委托管家代办」",
                        "overdueIncome", overdue.stream().mapToLong(FarmServiceOrderDO::getPrice).sum(), "overdueCount", overdue.size()),
                "compliance", m("pesticideBlocked", pesticide, "rules", rules));
    }
}
