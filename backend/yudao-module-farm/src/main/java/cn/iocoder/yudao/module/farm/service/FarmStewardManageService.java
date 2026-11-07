package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.farm.controller.admin.stewardmanage.vo.FarmStewardManagePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.stewardmanage.vo.FarmStewardManageRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmStewardDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmStewardMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管家管理（mock：routes/farm-people.ts 的 buildStewards / overview，口径一致）。只读，由管家账号 + 认养 + 任务 + 代管工单派生。
 * 管家名单取 farm_steward（对应 mock 的 users.kind=steward）；id 为 farm_steward.user_id。
 */
@Service
public class FarmStewardManageService {

    @Autowired
    private FarmStewardMapper stewardMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmViewLookup lookup;
    @Autowired
    private AdminUserApi adminUserApi;

    private static long ms(LocalDateTime t) {
        return FarmAppSupport.toMillis(t);
    }

    /** 内部行：带完整手机号用于搜索 */
    private static class Row {
        FarmStewardManageRespVO vo = new FarmStewardManageRespVO();
        String mobileFull = "";
    }

    private List<Row> build(LocalDateTime now) {
        List<FarmStewardDO> stewards = stewardMapper.selectList(new LambdaQueryWrapper<FarmStewardDO>().orderByAsc(FarmStewardDO::getId));
        if (stewards.isEmpty()) return new ArrayList<>();
        List<Long> userIds = stewards.stream().map(FarmStewardDO::getUserId).collect(Collectors.toList());
        Map<Long, AdminUserRespDTO> users = new HashMap<>();
        List<AdminUserRespDTO> ul = adminUserApi.getUserList(userIds);
        if (ul != null) for (AdminUserRespDTO u : ul) users.put(u.getId(), u);
        Map<Long, FarmBaseDO> bases = lookup.bases(stewards.stream().map(FarmStewardDO::getBaseId).collect(Collectors.toList()));
        LocalDateTime m0 = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime m1 = m0.plusMonths(1);

        List<FarmAssetDO> assets = assetMapper.selectList(new LambdaQueryWrapper<>());
        List<FarmAdoptionDO> ads = adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>()
                .eq(FarmAdoptionDO::getStatus, 10).in(FarmAdoptionDO::getStewardId, userIds));
        List<FarmTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<FarmTaskDO>()
                .in(FarmTaskDO::getStewardId, userIds).ge(FarmTaskDO::getDeadline, m0).lt(FarmTaskDO::getDeadline, m1)
                .in(FarmTaskDO::getStatus, 0, 10));
        List<FarmServiceOrderDO> accepted = serviceOrderMapper.selectList(new LambdaQueryWrapper<FarmServiceOrderDO>()
                .in(FarmServiceOrderDO::getStewardId, userIds).isNotNull(FarmServiceOrderDO::getAcceptTime));

        List<Row> out = new ArrayList<>();
        for (FarmStewardDO s : stewards) {
            Long uid = s.getUserId();
            AdminUserRespDTO u = users.get(uid);
            FarmBaseDO base = bases.get(s.getBaseId());
            List<FarmAssetDO> baseAssets = assets.stream().filter(a -> s.getBaseId() != null && s.getBaseId().equals(a.getBaseId())).collect(Collectors.toList());
            List<FarmTaskDO> myTasks = tasks.stream().filter(t -> uid.equals(t.getStewardId())).collect(Collectors.toList());
            long done = myTasks.stream().filter(t -> t.getStatus() != null && t.getStatus() == 10).count();
            List<FarmServiceOrderDO> myAcc = accepted.stream().filter(x -> uid.equals(x.getStewardId())).collect(Collectors.toList());
            Row r = new Row();
            FarmStewardManageRespVO v = r.vo;
            r.mobileFull = u == null || u.getMobile() == null ? "" : u.getMobile();
            v.setId(uid);
            v.setName(u == null || u.getNickname() == null ? "" : u.getNickname());
            v.setMobile(FarmCustomerService.maskMobile(r.mobileFull));
            v.setRoleName("管家");
            v.setBaseId(s.getBaseId());
            v.setBaseName(base == null || base.getName() == null ? "" : base.getName());
            v.setAssetTotal(baseAssets.size());
            v.setAssetAdopted((int) baseAssets.stream().filter(a -> a.getStatus() != null && a.getStatus() == 10).count());
            v.setMemberCount((int) ads.stream().filter(a -> uid.equals(a.getStewardId())).map(FarmAdoptionDO::getMemberId).distinct().count());
            v.setMonthTaskTotal(myTasks.size());
            v.setMonthTaskDone((int) done);
            v.setMonthTaskRate(myTasks.isEmpty() ? null : (int) Math.round((double) done / myTasks.size() * 100));
            v.setAvgResponseMinutes(myAcc.isEmpty() ? null
                    : Math.round((double) myAcc.stream().mapToLong(x -> ms(x.getAcceptTime()) - ms(x.getCreateTime())).sum() / myAcc.size() / 60000));
            v.setStatus(0);
            out.add(r);
        }
        return out;
    }

    public PageResult<FarmStewardManageRespVO> page(FarmStewardManagePageReqVO req) {
        List<FarmStewardManageRespVO> rows = build(LocalDateTime.now()).stream()
                .filter(r -> StrUtil.isEmpty(req.getKeyword()) || r.vo.getName().contains(req.getKeyword()) || r.mobileFull.contains(req.getKeyword()))
                .filter(r -> req.getBaseId() == null || req.getBaseId().equals(r.vo.getBaseId()))
                .map(r -> r.vo).collect(Collectors.toList());
        int size = req.getPageSize() == null || req.getPageSize() <= 0 ? Math.max(rows.size(), 1) : Math.min(200, req.getPageSize());
        int no = Math.max(1, req.getPageNo() == null ? 1 : req.getPageNo());
        int from = Math.min(rows.size(), (no - 1) * size);
        int to = Math.min(rows.size(), from + size);
        return new PageResult<>(new ArrayList<>(rows.subList(from, to)), (long) rows.size());
    }

    /** 本月绩效排行 + 今日工作量。绩效只有「任务完成率」一个口径 */
    public Map<String, Object> overview() {
        LocalDateTime now = LocalDateTime.now();
        List<FarmStewardManageRespVO> rows = build(now).stream().map(r -> r.vo).collect(Collectors.toList());
        List<Map<String, Object>> ranking = rows.stream().filter(s -> s.getMonthTaskRate() != null)
                .sorted(Comparator.comparing(FarmStewardManageRespVO::getMonthTaskRate).reversed()
                        .thenComparing(Comparator.comparing(FarmStewardManageRespVO::getMemberCount).reversed()))
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("score", s.getMonthTaskRate());
                    return m;
                }).collect(Collectors.toList());

        LocalDateTime dayEnd = now.toLocalDate().plusDays(1).atStartOfDay();
        Set<Long> ids = rows.stream().map(FarmStewardManageRespVO::getId).collect(Collectors.toSet());
        long pendingTasks = ids.isEmpty() ? 0 : taskMapper.selectCount(new LambdaQueryWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getOwner, "steward").eq(FarmTaskDO::getStatus, 0)
                .in(FarmTaskDO::getStewardId, ids).lt(FarmTaskDO::getDeadline, dayEnd));
        Map<String, Object> today = new LinkedHashMap<>();
        today.put("pendingTasks", pendingTasks);
        today.put("pendingRecords", recordMapper.selectCount(new LambdaQueryWrapper<cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO>()
                .eq(cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO::getStatus, 0)));
        today.put("pendingServiceOrders", serviceOrderMapper.selectCount(new LambdaQueryWrapper<FarmServiceOrderDO>()
                .eq(FarmServiceOrderDO::getStatus, 0)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ranking", ranking);
        out.put("today", today);
        return out;
    }
}
