package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.dashboard.vo.FarmRecordViewVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
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
import jakarta.annotation.Resource;
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
 * 工作台（契约 2.1；对应 mock-server/src/routes/farm-admin.ts 第 19–67 行）。
 * 租户隔离由芋道租户拦截器自动处理，这里不再手写 tenant_id。
 */
@Service
public class FarmDashboardService {

    private static final long DAY = 86400_000L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Resource
    private FarmOrderMapper orderMapper;
    @Resource
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Resource
    private FarmAssetMapper assetMapper;
    @Resource
    private FarmAdoptionMapper adoptionMapper;
    @Resource
    private FarmRecordMapper recordMapper;
    @Resource
    private FarmServiceOrderMapper serviceOrderMapper;
    @Resource
    private FarmTaskMapper taskMapper;
    @Resource
    private FarmHarvestMapper harvestMapper;
    @Resource
    private FarmMemberMapper memberMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private FarmRiskService riskService;

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

    public Map<String, Object> summary(Long tenantId) {
        LocalDate today = LocalDate.now(ZONE);
        long startToday = epochMs(today);
        LocalDate monthFirst = today.withDayOfMonth(1);
        long monthStart = epochMs(monthFirst);
        long now = System.currentTimeMillis();

        // 取近 14 天和本月月初两者更早的起点之后的已支付认养订单、农资订单（农资订单创建即计入），在内存里按天累加
        LocalDateTime earliest = fromMs(Math.min(monthStart, startToday - 13 * DAY));
        List<FarmOrderDO> paid = orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>()
                .isNotNull(FarmOrderDO::getPayTime).ge(FarmOrderDO::getPayTime, earliest));
        List<FarmSupplyOrderDO> supplyPaid = supplyOrderMapper.selectList(new LambdaQueryWrapper<FarmSupplyOrderDO>()
                .ge(FarmSupplyOrderDO::getCreateTime, earliest));

        List<Map<String, Object>> gmvTrend = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            LocalDate d = today.minusDays(13 - i);
            long dayIndex = Math.floorDiv(epochMs(d), DAY);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
            m.put("amount", baselineGmv(tenantId, dayIndex) + incomeOn(paid, supplyPaid, d));
            gmvTrend.add(m);
        }
        long gmvToday = (Long) gmvTrend.get(13).get("amount");
        long gmvMonth = 0;
        for (LocalDate d = monthFirst; !d.isAfter(today); d = d.plusDays(1)) {
            gmvMonth += baselineGmv(tenantId, Math.floorDiv(epochMs(d), DAY)) + incomeOn(paid, supplyPaid, d);
        }
        long orderCountMonth = paid.stream().filter(o -> toMs(o.getPayTime()) >= monthStart).count();

        List<FarmAssetDO> assets = assetMapper.selectList(new LambdaQueryWrapper<FarmAssetDO>().ne(FarmAssetDO::getStatus, 20));
        List<FarmAdoptionDO> adoptions = adoptionMapper.selectList(FarmAdoptionDO::getStatus, 10);
        long pendingRecordCount = recordMapper.selectCount(FarmRecordDO::getStatus, 0);
        long pendingServiceOrderCount = serviceOrderMapper.selectCount(FarmServiceOrderDO::getStatus, 0);
        long overdueSelfTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getOwner, "member").eq(FarmTaskDO::getStatus, 0)
                .lt(FarmTaskDO::getDeadline, fromMs(now)));
        long unshipped = harvestMapper.selectCount(new LambdaQueryWrapper<FarmHarvestDO>().isNull(FarmHarvestDO::getShipmentId));
        long untraced = supplyOrderMapper.selectCount(FarmSupplyOrderDO::getTraceState, 0);

        List<Map<String, Object>> careModeShare = new ArrayList<>();
        String[][] modes = {{"full", "全托管"}, {"semi", "半托管"}, {"self", "不托管"}};
        for (String[] mode : modes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mode", mode[0]);
            m.put("name", mode[1]);
            m.put("count", adoptions.stream().filter(a -> mode[0].equals(a.getCareMode())).count());
            careModeShare.add(m);
        }

        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(todo("record", "农事记录待审核", pendingRecordCount, "/farm-work/record"));
        todos.add(todo("service", "代管工单待接单", pendingServiceOrderCount, "/farm-work/service-order"));
        todos.add(todo("harvest", "收成待发货", unshipped, "/farm-order/shipment"));
        todos.add(todo("trace", "农资待登记施用", untraced, "/farm-supply/supply-order"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gmvToday", gmvToday);
        out.put("gmvMonth", gmvMonth);
        out.put("orderCountMonth", orderCountMonth);
        out.put("adoptingCount", adoptions.size());
        out.put("assetTotal", assets.size());
        out.put("assetAdopted", assets.stream().filter(a -> a.getStatus() != null && a.getStatus() == 10).count());
        out.put("pendingRecordCount", pendingRecordCount);
        out.put("pendingServiceOrderCount", pendingServiceOrderCount);
        out.put("overdueSelfTaskCount", overdueSelfTaskCount);
        out.put("riskScore", riskService.selfCheck().get("score"));
        out.put("gmvTrend", gmvTrend);
        out.put("careModeShare", careModeShare);
        out.put("todos", todos);
        out.put("todayRecords", todayRecords(fromMs(startToday - DAY)));
        out.put("demo", true);
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

    private static Map<String, Object> todo(String type, String title, long count, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("title", title);
        m.put("count", count);
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
