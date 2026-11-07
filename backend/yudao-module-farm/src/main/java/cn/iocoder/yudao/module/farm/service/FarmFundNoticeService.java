package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmFundNoticeDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmFundNoticeMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.m;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;

/**
 * 资金公示（第二期 B；mock：routes/farm-ext-b.ts 的 /fund-notice/**）。
 * 公示开关存 farm_fund_notice（每租户一行）；预览数据全部派生；资金用途占比为演示口径，只有平台服务费一行是真实 3%。
 */
@Service
public class FarmFundNoticeService {

    /** key, label, 默认是否公示（= 建议公示） */
    private static final Object[][] FIELDS = {
            {"flowChart", "资金流向图", true}, {"payeeName", "收款主体名称", true}, {"channelName", "支付通道名称", true},
            {"feeRate", "平台服务费率", true}, {"policyNo", "保单编号", true}, {"monthlyTotal", "月度认养总额", false}, {"memberTotal", "认养人总数", false}};

    @Autowired
    private FarmFundNoticeMapper noticeMapper;
    @Autowired
    private FarmFinanceService financeService;
    @Autowired
    private FarmCertService certService;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private TenantService tenantService;

    private FarmFundNoticeDO row() {
        return noticeMapper.selectOne(new LambdaQueryWrapperX<FarmFundNoticeDO>().orderByAsc(FarmFundNoticeDO::getId).last("LIMIT 1"));
    }

    public Map<String, Object> get() {
        FarmFundNoticeDO row = row();
        Map<String, Object> saved = row == null || row.getFields() == null ? new LinkedHashMap<>() : row.getFields();
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object[] f : FIELDS) {
            Object v = saved.get((String) f[0]);
            fields.add(m("key", f[0], "label", f[1], "recommend", f[2], "enabled", v instanceof Boolean ? (Boolean) v : (Boolean) f[2]));
        }
        TenantDO tenant = tenantService.getTenant(TenantContextHolder.getRequiredTenantId());
        long[] tf = financeService.last30TotalAndFee();
        long total = tf[0];
        long fee = tf[1];
        double[] pct = {13.1, 31.3, 9.7, 2.7, 18.2};
        String[] names = {"种苗 / 农资投入", "人工与管家成本", "物流配送", "保险保费", "基地维护与折旧"};
        List<Map<String, Object>> usage = new ArrayList<>();
        long used = 0;
        double pctSum = 0;
        for (int i = 0; i < pct.length; i++) {
            long amt = Math.round(total * pct[i] / 100);
            used += amt;
            pctSum += pct[i];
            usage.add(m("name", names[i], "percent", pct[i], "amount", amt, "demo", true));
        }
        double feePct = FarmFinanceService.FEE_RATE * 100;
        usage.add(m("name", "平台技术服务费", "percent", feePct, "amount", fee, "demo", false));
        usage.add(m("name", "经营毛利", "percent", Math.round((100 - pctSum - feePct) * 10) / 10.0, "amount", total - used - fee, "demo", true));

        String policyNo = certService.all().stream().filter(c -> String.valueOf(c.get("name")).contains("保单"))
                .map(c -> String.valueOf(c.get("no"))).findFirst().orElse("");
        long members = adoptionMapper.selectList().stream().map(FarmAdoptionDO::getMemberId).distinct().count();
        Map<String, Object> preview = m("steps", Arrays.asList("消费者支付", "第三方支付通道", "经营者对公账户", "按生产进度结算"),
                "payeeName", tenant == null ? "" : tenant.getName(), "channelName", "微信支付", "feeRate", feePct, "policyNo", policyNo,
                "monthlyTotal", total, "memberTotal", members);
        return m("demo", true, "fields", fields, "preview", preview, "usage", usage, "usageTotal", total,
                "updateTime", row == null || row.getUpdateTime() == null ? null : row.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public void save(Map<String, Object> fields) {
        if (fields == null) throw exception(BAD_REQUEST, "缺少公示字段");
        FarmFundNoticeDO row = row();
        Map<String, Object> merged = row == null || row.getFields() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(row.getFields());
        for (Object[] f : FIELDS) {
            Object v = fields.get((String) f[0]);
            if (v instanceof Boolean) merged.put((String) f[0], v);
            else if (!merged.containsKey((String) f[0])) merged.put((String) f[0], f[2]);
        }
        if (row == null) {
            row = new FarmFundNoticeDO();
            row.setFields(merged);
            noticeMapper.insert(row);
        } else {
            row.setFields(merged);
            noticeMapper.updateById(row);
        }
    }
}
