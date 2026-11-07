package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardAssetDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardCheckinReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardCheckinRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardHomeRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardTaskDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.steward.vo.FarmStewardTaskRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmStewardDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmStewardMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.TASK_NOT_MINE;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.TASK_STATUS_CHANGED;

/**
 * 管家端（契约 3；mock：mock-server/src/routes/farm-steward.ts）。
 * 管家 = 芋道后台账号，与基地的绑定在 farm_steward。只能看、只能做分给自己的。
 */
@Service
public class FarmStewardService {

    /** 契约 1_030_000_006：状态已变化（提示语由调用处传入；工单被接走的提示与任务不同） */
    private static final ErrorCode STATE_CHANGED = new ErrorCode(1_030_000_006, "{}");

    @Autowired
    private FarmStewardMapper stewardMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmBaseMapper baseMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmViewLookup lookup;
    @Autowired
    private FarmAssetService assetService;
    @Autowired
    private FarmRecordService recordService;

    // ---------- 当前管家（mock requireSteward） ----------

    private FarmStewardDO currentSteward() {
        Long uid = SecurityFrameworkUtils.getLoginUserId();
        List<FarmStewardDO> list = uid == null ? new ArrayList<>() : stewardMapper.selectList(
                new LambdaQueryWrapper<FarmStewardDO>().eq(FarmStewardDO::getUserId, uid).orderByAsc(FarmStewardDO::getId));
        if (list.isEmpty()) throw exception(FORBIDDEN, "只有管家账号可以使用管家端");
        return list.get(0);
    }

    /** 本基地待接单的工单（mock poolOf）：status=0 且资产属于管家所在基地 */
    private List<FarmServiceOrderDO> pool(FarmStewardDO st) {
        if (st.getBaseId() == null) return new ArrayList<>();
        List<Long> assetIds = assetMapper.selectList(new LambdaQueryWrapper<FarmAssetDO>()
                        .eq(FarmAssetDO::getBaseId, st.getBaseId()))
                .stream().map(FarmAssetDO::getId).collect(Collectors.toList());
        if (assetIds.isEmpty()) return new ArrayList<>();
        return serviceOrderMapper.selectList(new LambdaQueryWrapper<FarmServiceOrderDO>()
                .eq(FarmServiceOrderDO::getStatus, 0)
                .in(FarmServiceOrderDO::getAssetId, assetIds)
                .orderByDesc(FarmServiceOrderDO::getCreateTime)
                .orderByDesc(FarmServiceOrderDO::getId));
    }

    // ---------- 首页（mock 15–34） ----------

    public FarmStewardHomeRespVO home() {
        FarmStewardDO st = currentSteward();
        Long uid = st.getUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startToday = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<FarmTaskDO> mine = taskMapper.selectList(new LambdaQueryWrapper<FarmTaskDO>().eq(FarmTaskDO::getStewardId, uid));

        FarmStewardHomeRespVO v = new FarmStewardHomeRespVO();
        v.setStewardName(lookup.userNames(List.of(uid)).getOrDefault(uid, ""));
        FarmBaseDO base = st.getBaseId() == null ? null : baseMapper.selectById(st.getBaseId());
        v.setBaseName(base == null || base.getName() == null ? "" : base.getName());

        FarmStewardHomeRespVO.Todo todo = new FarmStewardHomeRespVO.Todo();
        todo.setTaskCount((int) mine.stream().filter(t -> isStatus(t, 0)).count());
        todo.setOverdueCount((int) mine.stream().filter(t -> isStatus(t, 0) && t.getDeadline() != null && t.getDeadline().isBefore(now)).count());
        todo.setServiceOrderPending(pool(st).size());
        todo.setServiceOrderMine(serviceOrderMapper.selectCount(new LambdaQueryWrapper<FarmServiceOrderDO>()
                .eq(FarmServiceOrderDO::getStewardId, uid).eq(FarmServiceOrderDO::getStatus, 10)));
        v.setTodo(todo);
        v.setTodayDone((int) mine.stream().filter(t -> isStatus(t, 10) && t.getFinishTime() != null && !t.getFinishTime().isBefore(startToday)).count());
        v.setMonthDone((int) mine.stream().filter(t -> isStatus(t, 10) && t.getFinishTime() != null && !t.getFinishTime().isBefore(monthStart)).count());
        v.setPendingAuditCount(recordMapper.selectCount(new LambdaQueryWrapper<FarmRecordDO>()
                .eq(FarmRecordDO::getStewardId, uid).eq(FarmRecordDO::getStatus, 0)));
        v.setRejectedCount(recordMapper.selectCount(new LambdaQueryWrapper<FarmRecordDO>()
                .eq(FarmRecordDO::getStewardId, uid).eq(FarmRecordDO::getStatus, 20)
                .gt(FarmRecordDO::getAuditTime, now.minusDays(7))));
        return v;
    }

    private static boolean isStatus(FarmTaskDO t, int s) {
        return t.getStatus() != null && t.getStatus() == s;
    }

    // ---------- 任务（mock 37–50） ----------

    public List<FarmStewardTaskRespVO> taskList(Integer statusParam) {
        FarmStewardDO st = currentSteward();
        int status = statusParam == null ? 0 : statusParam;
        List<FarmTaskDO> rows = new ArrayList<>(taskMapper.selectList(new LambdaQueryWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getStewardId, st.getUserId()).eq(FarmTaskDO::getStatus, status)));
        if (status == 0) {
            // 待办：截止时间升序
            rows.sort(Comparator.comparing(FarmTaskDO::getDeadline, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())));
        } else {
            // 其他：完成时间倒序，空值当 0 排最后
            rows.sort(Comparator.comparing(FarmTaskDO::getFinishTime, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())).reversed());
        }
        return taskViews(rows);
    }

    public FarmStewardTaskDetailRespVO taskGet(Long id) {
        FarmStewardDO st = currentSteward();
        FarmTaskDO t = id == null ? null : taskMapper.selectById(id);
        if (t == null) throw exception(NOT_FOUND, "任务");
        if (!st.getUserId().equals(t.getStewardId())) throw exception(TASK_NOT_MINE);
        FarmStewardTaskRespVO base = taskViews(List.of(t)).get(0);
        FarmStewardTaskDetailRespVO v = BeanUtils.toBean(base, FarmStewardTaskDetailRespVO.class);
        FarmAssetDO a = t.getAssetId() == null ? null : assetMapper.selectById(t.getAssetId());
        if (a != null) {
            FarmStewardTaskDetailRespVO.AssetBrief b = new FarmStewardTaskDetailRespVO.AssetBrief();
            b.setId(a.getId());
            b.setCode(a.getCode());
            b.setName(a.getName());
            b.setCrop(crop(a));
            b.setAreaM2(a.getSpecs() == null ? null : a.getSpecs().get("areaM2"));
            b.setCameraName(a.getCameraName());
            v.setAsset(b);
            v.setEnv(currentEnv(a.getId()));
        }
        return v;
    }

    /** mock：a.specs.crop ?? a.specs.breed ?? '' */
    private static String crop(FarmAssetDO a) {
        Map<String, Object> s = a.getSpecs();
        if (s == null) return "";
        Object c = s.get("crop");
        if (c == null) c = s.get("breed");
        return c == null ? "" : String.valueOf(c);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> currentEnv(Long assetId) {
        return (Map<String, Object>) assetService.env(assetId).get("current");
    }

    /** 打卡：生成待审核农事记录，任务置完成；来自代管工单的任务，工单同时置完成（mock 51–82） */
    @Transactional(rollbackFor = Exception.class)
    public FarmStewardCheckinRespVO checkin(FarmStewardCheckinReqVO req) {
        FarmStewardDO st = currentSteward();
        Long uid = st.getUserId();
        FarmTaskDO t = req.getTaskId() == null ? null : taskMapper.selectById(req.getTaskId());
        if (t == null) throw exception(NOT_FOUND, "任务");
        if (!uid.equals(t.getStewardId())) throw exception(TASK_NOT_MINE);
        if (!isStatus(t, 0)) throw exception(TASK_STATUS_CHANGED);
        List<String> photos = req.getPhotos() == null ? new ArrayList<>()
                : req.getPhotos().stream().filter(StrUtil::isNotEmpty).collect(Collectors.toList());
        if (photos.isEmpty()) throw exception(BAD_REQUEST, "至少拍一张现场照片");
        if (photos.size() > 9) throw exception(BAD_REQUEST, "最多上传 9 张照片");

        FarmAssetDO a = t.getAssetId() == null ? null : assetMapper.selectById(t.getAssetId());
        if (a == null) throw exception(NOT_FOUND, "标的");
        FarmBaseDO base = a.getBaseId() == null ? null : baseMapper.selectById(a.getBaseId());
        double baseLng = base == null || base.getLng() == null ? 0 : base.getLng().doubleValue();
        double baseLat = base == null || base.getLat() == null ? 0 : base.getLat().doubleValue();
        // Number(b.lng) || base.lng || 0：没传或为 0 就用基地坐标
        double lng = req.getLng() != null && req.getLng().signum() != 0 ? req.getLng().doubleValue() : baseLng;
        double lat = req.getLat() != null && req.getLat().signum() != 0 ? req.getLat().doubleValue() : baseLat;
        // GPS 围栏：离基地中心 2 公里以内算在基地里（演示用简化算法，与 mock 一致）
        boolean inFence = base != null && Math.hypot((lng - baseLng) * 96, (lat - baseLat) * 111) <= 2;

        LocalDateTime now = LocalDateTime.now();
        // 先带 status=0 条件抢占任务，防止并发重复完成
        FarmTaskDO tu = new FarmTaskDO();
        tu.setStatus(10);
        tu.setFinishTime(now);
        int rows = taskMapper.update(tu, new LambdaUpdateWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getId, t.getId()).eq(FarmTaskDO::getStatus, 0));
        if (rows == 0) throw exception(TASK_STATUS_CHANGED);

        Map<String, Object> cur = currentEnv(a.getId());
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("airTemp", cur.get("airTemp"));
        env.put("airHumidity", cur.get("airHumidity"));
        env.put("soilMoisture", cur.get("soilMoisture"));
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("exifTime", true);
        checks.put("gpsFence", inFence);
        checks.put("duplicate", true);

        String content = req.getContent() == null ? "" : req.getContent().trim();
        FarmRecordDO r = new FarmRecordDO();
        r.setAdoptionId(t.getAdoptionId());
        r.setAssetId(t.getAssetId());
        r.setStewardId(uid);
        r.setTaskId(t.getId());
        r.setKind(t.getServiceOrderId() != null ? "service" : "farming");
        r.setTitle(StrUtil.nullToEmpty(t.getTitle()).replaceFirst("^[A-Z]-\\d+\\s*", ""));
        r.setContent(content.isEmpty() ? StrUtil.nullToEmpty(t.getDescription()) : content);
        r.setPhotos(photos);
        r.setLng(BigDecimal.valueOf(lng));
        r.setLat(BigDecimal.valueOf(lat));
        r.setEnv(env);
        r.setChecks(checks);
        r.setStatus(0);
        recordMapper.insert(r);

        if (t.getServiceOrderId() != null) {
            FarmServiceOrderDO su = new FarmServiceOrderDO();
            su.setId(t.getServiceOrderId());
            su.setStatus(20);
            su.setFinishTime(now);
            su.setProofPhotos(photos);
            serviceOrderMapper.updateById(su);
        }

        FarmStewardCheckinRespVO v = new FarmStewardCheckinRespVO();
        v.setRecordId(r.getId());
        v.setChecks(checks);
        v.setEnv(env);
        return v;
    }

    // ---------- 代管工单（mock 85–110） ----------

    public List<FarmServiceOrderRespVO> serviceOrderList(String scope) {
        FarmStewardDO st = currentSteward();
        List<FarmServiceOrderDO> rows;
        if ("mine".equals(scope)) {
            rows = serviceOrderMapper.selectList(new LambdaQueryWrapper<FarmServiceOrderDO>()
                    .eq(FarmServiceOrderDO::getStewardId, st.getUserId())
                    .orderByDesc(FarmServiceOrderDO::getCreateTime)
                    .orderByDesc(FarmServiceOrderDO::getId));
        } else {
            rows = pool(st);
        }
        return serviceOrderViews(rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public void serviceOrderAccept(Long id) {
        FarmStewardDO st = currentSteward();
        Long uid = st.getUserId();
        FarmServiceOrderDO so = id == null ? null : serviceOrderMapper.selectById(id);
        if (so == null) throw exception(NOT_FOUND, "代管工单");
        if (so.getStatus() == null || so.getStatus() != 0) throw exception(STATE_CHANGED, "工单已被接走，请刷新");
        LocalDateTime now = LocalDateTime.now();
        FarmServiceOrderDO u = new FarmServiceOrderDO();
        u.setStatus(10);
        u.setStewardId(uid);
        u.setAcceptTime(now);
        // 带 status=0 条件，防止两个管家同时接
        int rows = serviceOrderMapper.update(u, new LambdaUpdateWrapper<FarmServiceOrderDO>()
                .eq(FarmServiceOrderDO::getId, so.getId()).eq(FarmServiceOrderDO::getStatus, 0));
        if (rows == 0) throw exception(STATE_CHANGED, "工单已被接走，请刷新");

        FarmAssetDO a = so.getAssetId() == null ? null : assetMapper.selectById(so.getAssetId());
        FarmTaskDO t = new FarmTaskDO();
        t.setAdoptionId(so.getAdoptionId());
        t.setAssetId(so.getAssetId());
        t.setOwner("steward");
        t.setStewardId(uid);
        t.setTitle((a == null ? "" : a.getCode()) + " " + StrUtil.nullToEmpty(so.getServiceType()) + "（代管工单）");
        t.setDescription(StrUtil.isNotEmpty(so.getRemark()) ? so.getRemark() : "认养人委托代办：" + StrUtil.nullToEmpty(so.getTaskTitle()));
        t.setSource("service");
        t.setServiceOrderId(so.getId());
        t.setDeadline(now.plusDays(1));
        t.setStatus(0);
        t.setEntrustPrice(null);
        t.setFinishTime(null);
        taskMapper.insert(t);
    }

    // ---------- 标的详情（mock 113–129） ----------

    public FarmStewardAssetDetailRespVO assetGet(Long id) {
        currentSteward();
        FarmAssetDO a = id == null ? null : assetMapper.selectById(id);
        if (a == null) throw exception(NOT_FOUND, "标的");
        List<FarmAdoptionDO> ads = adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>()
                .eq(FarmAdoptionDO::getAssetId, a.getId()).eq(FarmAdoptionDO::getStatus, 10).orderByAsc(FarmAdoptionDO::getId));
        FarmAdoptionDO ad = ads.isEmpty() ? null : ads.get(0);
        FarmMemberDO m = ad == null || ad.getMemberId() == null ? null : memberMapper.selectById(ad.getMemberId());

        FarmStewardAssetDetailRespVO v = new FarmStewardAssetDetailRespVO();
        FarmAssetRespVO av = BeanUtils.toBean(a, FarmAssetRespVO.class);
        FarmBaseDO base = a.getBaseId() == null ? null : baseMapper.selectById(a.getBaseId());
        av.setBaseName(base == null || base.getName() == null ? "" : base.getName());
        v.setAsset(av);

        if (ad != null) {
            FarmStewardAssetDetailRespVO.Adoption x = new FarmStewardAssetDetailRespVO.Adoption();
            x.setId(ad.getId());
            x.setCareMode(ad.getCareMode());
            x.setCareModeName(FarmViewLookup.careModeName(ad.getCareMode()));
            x.setStartAt(ad.getStartAt());
            x.setEndAt(ad.getEndAt());
            x.setPromisedJin(ad.getPromisedJin());
            x.setHarvestedJin(ad.getHarvestedJin());
            v.setAdoption(x);
        }
        if (m != null) {
            FarmStewardAssetDetailRespVO.Member x = new FarmStewardAssetDetailRespVO.Member();
            x.setName(m.getNickname());
            x.setMobile(maskMobile(m.getMobile()));
            v.setMember(x);
        }
        v.setEnv(assetService.env(a.getId()));

        List<FarmRecordDO> recs = recordMapper.selectList(new LambdaQueryWrapper<FarmRecordDO>()
                .eq(FarmRecordDO::getAssetId, a.getId())
                .orderByDesc(FarmRecordDO::getCreateTime).orderByDesc(FarmRecordDO::getId).last("LIMIT 5"));
        List<FarmRecordRespVO> records = recs.isEmpty() ? new ArrayList<>() : recordService.toViews(recs);
        v.setRecords(records);

        LocalDateTime now = LocalDateTime.now();
        List<FarmTaskDO> selfTasks = new ArrayList<>();
        if (ad != null) {
            selfTasks = taskMapper.selectList(new LambdaQueryWrapper<FarmTaskDO>()
                    .eq(FarmTaskDO::getAdoptionId, ad.getId()).eq(FarmTaskDO::getOwner, "member")
                    .ge(FarmTaskDO::getDeadline, now.minusDays(7)));
        }
        FarmStewardAssetDetailRespVO.SelfTaskWeek w = new FarmStewardAssetDetailRespVO.SelfTaskWeek();
        w.setTotal(selfTasks.size());
        w.setDone((int) selfTasks.stream().filter(t -> isStatus(t, 10)).count());
        w.setEntrusted((int) selfTasks.stream().filter(t -> isStatus(t, 20)).count());
        w.setOverdue((int) selfTasks.stream().filter(t -> isStatus(t, 0) && t.getDeadline() != null && t.getDeadline().isBefore(now)).count());
        v.setSelfTaskWeek(w);
        return v;
    }

    /** mock maskMobile：11 位手机号中间 4 位打星 */
    private static String maskMobile(String m) {
        if (m == null) return "";
        return m.replaceFirst("^(\\d{3})\\d{4}(\\d{4})$", "$1****$2");
    }

    // ---------- 视图拼装 ----------

    /** mock taskView：assetCode / baseName / memberName / careMode / overdue */
    private List<FarmStewardTaskRespVO> taskViews(List<FarmTaskDO> list) {
        List<FarmStewardTaskRespVO> out = new ArrayList<>();
        if (list.isEmpty()) return out;
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmTaskDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmBaseDO> bases = lookup.bases(assets.values().stream().map(FarmAssetDO::getBaseId).collect(Collectors.toList()));
        Map<Long, FarmAdoptionDO> ads = lookup.adoptions(list.stream().map(FarmTaskDO::getAdoptionId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(ads.values().stream().map(FarmAdoptionDO::getMemberId).collect(Collectors.toList()));
        LocalDateTime now = LocalDateTime.now();
        for (FarmTaskDO t : list) {
            FarmStewardTaskRespVO v = BeanUtils.toBean(t, FarmStewardTaskRespVO.class);
            FarmAssetDO a = t.getAssetId() == null ? null : assets.get(t.getAssetId());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            FarmBaseDO b = a == null || a.getBaseId() == null ? null : bases.get(a.getBaseId());
            v.setBaseName(b == null || b.getName() == null ? "" : b.getName());
            FarmAdoptionDO ad = t.getAdoptionId() == null ? null : ads.get(t.getAdoptionId());
            FarmMemberDO m = ad == null || ad.getMemberId() == null ? null : members.get(ad.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setCareMode(ad == null ? null : ad.getCareMode());
            v.setOverdue(isStatus(t, 0) && t.getDeadline() != null && t.getDeadline().isBefore(now));
            out.add(v);
        }
        return out;
    }

    /** mock serviceOrderView：assetCode / memberName / stewardName */
    private List<FarmServiceOrderRespVO> serviceOrderViews(List<FarmServiceOrderDO> list) {
        List<FarmServiceOrderRespVO> out = new ArrayList<>();
        if (list.isEmpty()) return out;
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmServiceOrderDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmServiceOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, String> users = lookup.userNames(list.stream().map(FarmServiceOrderDO::getStewardId).collect(Collectors.toList()));
        for (FarmServiceOrderDO s : list) {
            FarmServiceOrderRespVO v = BeanUtils.toBean(s, FarmServiceOrderRespVO.class);
            FarmAssetDO a = s.getAssetId() == null ? null : assets.get(s.getAssetId());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            FarmMemberDO m = s.getMemberId() == null ? null : members.get(s.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setStewardName(s.getStewardId() == null ? "" : users.getOrDefault(s.getStewardId(), ""));
            out.add(v);
        }
        return out;
    }
}
