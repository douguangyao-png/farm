package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentCreateReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.*;
import cn.iocoder.yudao.module.farm.dal.mysql.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 管家端第二期补充（mock：mock-server/src/routes/farm-steward-extra.ts）。
 * 范围：管家只能看、只能做 farm_adoption.steward_id = 当前登录账号 的认养。
 */
@Service
public class FarmStewardExtraService {

    private static final ErrorCode NOT_MINE = new ErrorCode(1_030_000_007, "{}");
    private static final ErrorCode STATE_CHANGED = new ErrorCode(1_030_000_006, "{}");
    private static final List<String> ALARM_TYPES = List.of("设备异常", "损耗登记", "气象灾害", "病虫害爆发");
    private static final Map<String, String> SUPPLY_KIND = Map.of("tool", "上门安装", "bio", "农场代施", "pesticide", "农场代施", "fert", "寄存 + 代施", "seed", "寄存 + 代播");
    private static final Map<String, String> SUPPLY_HINT = Map.of(
            "tool", "认养人已付款，需 48h 内完成安装并拍照",
            "bio", "限持证技术人员施用，施用后登记用量",
            "pesticide", "限持证技术人员施用，施用后登记用量",
            "fert", "整地时同步施入，需在打卡中登记用量",
            "seed", "播种时同步使用，需在打卡中登记用量");

    @Autowired
    private FarmStewardMapper stewardMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmHarvestMapper harvestMapper;
    @Autowired
    private FarmShipmentMapper shipmentMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmSupplyMapper supplyMapper;
    @Autowired
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Autowired
    private FarmInputRecordMapper inputRecordMapper;
    @Autowired
    private FarmBaseMapper baseMapper;
    @Autowired
    private FarmStewardAlarmMapper alarmMapper;
    @Autowired
    private FarmStewardMessageMapper messageMapper;
    @Autowired
    private FarmStewardRemindMapper remindMapper;
    @Autowired
    private FarmShipmentService shipmentService;
    @Autowired
    private FarmRecordService recordService;
    @Autowired
    private FarmViewLookup lookup;

    // ---------- 当前管家 / 我负责的认养 ----------

    private FarmStewardDO currentSteward() {
        Long uid = SecurityFrameworkUtils.getLoginUserId();
        List<FarmStewardDO> list = uid == null ? new ArrayList<>() : stewardMapper.selectList(
                new LambdaQueryWrapper<FarmStewardDO>().eq(FarmStewardDO::getUserId, uid).orderByAsc(FarmStewardDO::getId));
        if (list.isEmpty()) throw exception(FORBIDDEN, "只有管家账号可以使用管家端");
        return list.get(0);
    }

    private List<FarmAdoptionDO> myAdoptions(FarmStewardDO st) {
        return adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>().eq(FarmAdoptionDO::getStewardId, st.getUserId()));
    }

    private Set<Long> myAdoptionIds(FarmStewardDO st) {
        return myAdoptions(st).stream().map(FarmAdoptionDO::getId).collect(Collectors.toSet());
    }

    private void assertMine(FarmStewardDO st, Long adoptionId) {
        if (adoptionId == null || !myAdoptionIds(st).contains(adoptionId)) throw exception(NOT_MINE, "只能处理自己负责的认养");
    }

    private String assetCode(Long assetId) {
        FarmAssetDO a = assetId == null ? null : assetMapper.selectById(assetId);
        return a == null || a.getCode() == null ? "" : a.getCode();
    }

    private String memberName(Long memberId) {
        FarmMemberDO m = memberId == null ? null : memberMapper.selectById(memberId);
        return m == null || m.getNickname() == null ? "" : m.getNickname();
    }

    private static boolean is(Integer v, int x) {
        return v != null && v == x;
    }

    // ---------- 配送确认 ----------

    public Map<String, Object> shipmentList(Integer status) {
        FarmStewardDO st = currentSteward();
        Set<Long> ids = myAdoptionIds(st);
        List<FarmShipmentDO> all = ids.isEmpty() ? new ArrayList<>() : shipmentMapper.selectList(new LambdaQueryWrapper<FarmShipmentDO>()
                .in(FarmShipmentDO::getAdoptionId, ids).orderByDesc(FarmShipmentDO::getCreateTime).orderByDesc(FarmShipmentDO::getId));
        List<FarmHarvestDO> harvests = ids.isEmpty() ? new ArrayList<>() : harvestMapper.selectList(new LambdaQueryWrapper<FarmHarvestDO>()
                .in(FarmHarvestDO::getAdoptionId, ids).isNull(FarmHarvestDO::getShipmentId).orderByDesc(FarmHarvestDO::getHarvestDate));
        Map<Long, Long> adoptionMember = myAdoptions(st).stream().collect(Collectors.toMap(FarmAdoptionDO::getId, FarmAdoptionDO::getMemberId, (a, b) -> a));
        List<Map<String, Object>> pending = new ArrayList<>();
        for (FarmHarvestDO h : harvests) {
            pending.add(FarmFulfillPage.m("harvestId", h.getId(), "crop", h.getCrop(), "weightJin", h.getWeightJin(),
                    "harvestDate", h.getHarvestDate(), "remark", h.getRemark() == null ? "" : h.getRemark(),
                    "assetCode", assetCode(h.getAssetId()), "memberName", memberName(adoptionMember.get(h.getAdoptionId()))));
        }
        List<FarmShipmentDO> rows = all.stream().filter(s -> status == null || is(s.getStatus(), status)).collect(Collectors.toList());
        boolean pendingTab = status == null || status == 0;
        return FarmFulfillPage.m(
                "stats", FarmFulfillPage.m("pending", pending.size(),
                        "shipped", all.stream().filter(s -> is(s.getStatus(), 10)).count(),
                        "done", all.stream().filter(s -> is(s.getStatus(), 20)).count()),
                "pending", pendingTab ? pending : new ArrayList<>(),
                "list", is(status, 0) ? new ArrayList<>() : shipmentService.toViews(rows));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long shipmentCreate(FarmShipmentCreateReqVO req) {
        FarmStewardDO st = currentSteward();
        FarmHarvestDO h = req.getHarvestId() == null ? null : harvestMapper.selectById(req.getHarvestId());
        if (h == null) throw exception(BAD_REQUEST, "请选择收成");
        assertMine(st, h.getAdoptionId());
        return shipmentService.create(req);
    }

    /** id 或扫码得到的配送单号 no */
    @Transactional(rollbackFor = Exception.class)
    public void shipmentSign(Long id, String no) {
        FarmStewardDO st = currentSteward();
        FarmShipmentDO s = id != null ? shipmentMapper.selectById(id)
                : StrUtil.isBlank(no) ? null : shipmentMapper.selectOne(new LambdaQueryWrapper<FarmShipmentDO>().eq(FarmShipmentDO::getNo, no.trim()).last("LIMIT 1"));
        if (s == null) throw exception(NOT_FOUND, "配送单");
        assertMine(st, s.getAdoptionId());
        shipmentService.sign(s.getId());
    }

    // ---------- 自管认养人指导 ----------

    public Map<String, Object> selfGuideList(String stateParam) {
        FarmStewardDO st = currentSteward();
        String wanted = StrUtil.isBlank(stateParam) ? "todo" : stateParam;
        Map<Long, FarmAdoptionDO> ads = myAdoptions(st).stream().collect(Collectors.toMap(FarmAdoptionDO::getId, a -> a, (a, b) -> a));
        List<FarmTaskDO> tasks = ads.isEmpty() ? new ArrayList<>() : taskMapper.selectList(new LambdaQueryWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getOwner, "member").in(FarmTaskDO::getAdoptionId, ads.keySet()));
        Map<Long, FarmStewardRemindDO> reminded = new HashMap<>();
        if (!tasks.isEmpty()) {
            for (FarmStewardRemindDO r : remindMapper.selectList(new LambdaQueryWrapper<FarmStewardRemindDO>()
                    .eq(FarmStewardRemindDO::getStewardId, st.getUserId())
                    .in(FarmStewardRemindDO::getTaskId, tasks.stream().map(FarmTaskDO::getId).collect(Collectors.toList())))) {
                reminded.put(r.getTaskId(), r);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        List<Map<String, Object>> all = new ArrayList<>();
        for (FarmTaskDO t : tasks) {
            FarmAdoptionDO ad = ads.get(t.getAdoptionId());
            FarmStewardRemindDO r = reminded.get(t.getId());
            boolean overdue = t.getDeadline() != null && t.getDeadline().isBefore(now);
            String state = !is(t.getStatus(), 0) ? "done" : overdue ? "over" : r != null ? "reminded" : "todo";
            Map<String, Object> v = FarmFulfillPage.m("taskId", t.getId(), "assetId", t.getAssetId(), "assetCode", assetCode(t.getAssetId()),
                    "memberId", ad.getMemberId(), "memberName", memberName(ad.getMemberId()), "careMode", ad.getCareMode(),
                    "careModeName", FarmViewLookup.careModeName(ad.getCareMode()), "title", t.getTitle(), "description", t.getDescription(),
                    "deadline", t.getDeadline(), "status", t.getStatus(), "state", state, "entrustPrice", t.getEntrustPrice(),
                    "remindedAt", r == null ? null : r.getRemindTime(), "entrusted", is(t.getStatus(), 20));
            all.add(v);
        }
        long weekTodo = tasks.stream().filter(t -> is(t.getStatus(), 0) && t.getDeadline() != null && !t.getDeadline().isBefore(weekAgo)).count();
        long weekReminded = all.stream().filter(v -> "reminded".equals(v.get("state"))).count();
        List<Map<String, Object>> list = all.stream().filter(v -> wanted.equals(v.get("state")))
                .sorted(Comparator.comparing((Map<String, Object> v) -> (LocalDateTime) v.get("deadline"), Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
                .collect(Collectors.toList());
        return FarmFulfillPage.m(
                "stats", FarmFulfillPage.m("plots", tasks.stream().map(FarmTaskDO::getAssetId).distinct().count(), "weekTodo", weekTodo,
                        "reminded", weekReminded, "overdue", all.stream().filter(v -> "over".equals(v.get("state"))).count()),
                "counts", FarmFulfillPage.m("todo", all.stream().filter(v -> "todo".equals(v.get("state"))).count(),
                        "reminded", weekReminded, "over", all.stream().filter(v -> "over".equals(v.get("state"))).count()),
                "list", list);
    }

    @Transactional(rollbackFor = Exception.class)
    public void selfGuideRemind(Long taskId, boolean entrust) {
        FarmStewardDO st = currentSteward();
        FarmTaskDO t = taskId == null ? null : taskMapper.selectById(taskId);
        if (t == null || !"member".equals(t.getOwner()) || t.getAdoptionId() == null) throw exception(NOT_FOUND, "自管任务");
        assertMine(st, t.getAdoptionId());
        if (!is(t.getStatus(), 0)) throw exception(STATE_CHANGED, "任务状态已变化，请刷新");
        FarmAdoptionDO ad = adoptionMapper.selectById(t.getAdoptionId());
        remindMapper.delete(new LambdaQueryWrapper<FarmStewardRemindDO>()
                .eq(FarmStewardRemindDO::getStewardId, st.getUserId()).eq(FarmStewardRemindDO::getTaskId, t.getId()));
        FarmStewardRemindDO r = new FarmStewardRemindDO();
        r.setStewardId(st.getUserId());
        r.setTaskId(t.getId());
        r.setEntrust(entrust);
        r.setRemindTime(LocalDateTime.now());
        remindMapper.insert(r);
        long price = t.getEntrustPrice() == null ? 6000 : t.getEntrustPrice();
        pushMessage(st.getUserId(), ad.getMemberId(), t.getAssetId(), "steward", entrust
                ? "「" + t.getTitle() + "」已逾期，可点「委托代办」交给我处理（¥" + price / 100 + "）。"
                : "提醒：「" + t.getTitle() + "」" + (t.getDeadline() != null && t.getDeadline().isBefore(LocalDateTime.now()) ? "已逾期" : "请按时完成") + "，有问题随时问我。");
    }

    // ---------- 我的记录 ----------

    public Map<String, Object> recordList(Integer status) {
        FarmStewardDO st = currentSteward();
        List<FarmRecordDO> mine = recordMapper.selectList(new LambdaQueryWrapper<FarmRecordDO>()
                .eq(FarmRecordDO::getStewardId, st.getUserId()).orderByDesc(FarmRecordDO::getCreateTime).orderByDesc(FarmRecordDO::getId));
        List<FarmRecordDO> rows = mine.stream().filter(r -> status == null || is(r.getStatus(), status)).limit(100).collect(Collectors.toList());
        List<FarmRecordRespVO> list = recordService.toViews(rows);
        return FarmFulfillPage.m(
                "stats", FarmFulfillPage.m("all", mine.size(), "pending", mine.stream().filter(r -> is(r.getStatus(), 0)).count(),
                        "published", mine.stream().filter(r -> is(r.getStatus(), 10)).count(), "rejected", mine.stream().filter(r -> is(r.getStatus(), 20)).count()),
                "list", list);
    }

    // ---------- 标的管理 ----------

    public Map<String, Object> assetList(String scopeParam) {
        FarmStewardDO st = currentSteward();
        String scope = StrUtil.isBlank(scopeParam) ? "all" : scopeParam;
        if (st.getBaseId() == null) return FarmFulfillPage.m("counts", FarmFulfillPage.m("all", 0, "raising", 0, "free", 0), "list", new ArrayList<>());
        List<FarmAssetDO> assets = assetMapper.selectList(new LambdaQueryWrapper<FarmAssetDO>().eq(FarmAssetDO::getBaseId, st.getBaseId()));
        Map<Long, FarmAdoptionDO> raising = new HashMap<>();
        if (!assets.isEmpty()) {
            for (FarmAdoptionDO ad : adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>()
                    .in(FarmAdoptionDO::getAssetId, assets.stream().map(FarmAssetDO::getId).collect(Collectors.toList())).eq(FarmAdoptionDO::getStatus, 10))) {
                raising.putIfAbsent(ad.getAssetId(), ad);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FarmAssetDO a : assets) {
            FarmAdoptionDO ad = raising.get(a.getId());
            Map<String, Object> specs = a.getSpecs() == null ? new HashMap<>() : a.getSpecs();
            Object crop = specs.get("crop") != null ? specs.get("crop") : specs.get("breed");
            int progress = 0;
            long leftDays = 0;
            if (ad != null && ad.getStartAt() != null && ad.getEndAt() != null) {
                long total = Math.max(1, Duration.between(ad.getStartAt(), ad.getEndAt()).toMillis());
                progress = (int) Math.min(100, Math.max(0, Math.round(Duration.between(ad.getStartAt(), now).toMillis() * 100.0 / total)));
                leftDays = Math.max(0, (long) Math.ceil(Duration.between(now, ad.getEndAt()).toMillis() / 86400000.0));
            }
            rows.add(FarmFulfillPage.m("id", a.getId(), "code", a.getCode(), "name", a.getName(), "assetType", a.getAssetType(),
                    "crop", crop == null ? "" : crop, "areaM2", specs.get("areaM2"), "raising", ad != null, "statusName", ad != null ? "在养" : "空闲",
                    "memberName", ad != null ? memberName(ad.getMemberId()) : "", "mine", ad != null && st.getUserId().equals(ad.getStewardId()),
                    "progress", progress, "harvestedJin", ad == null || ad.getHarvestedJin() == null ? 0 : ad.getHarvestedJin(),
                    "promisedJin", ad == null ? null : ad.getPromisedJin(), "leftDays", leftDays));
        }
        List<Map<String, Object>> list = rows.stream()
                .filter(r -> "raising".equals(scope) ? Boolean.TRUE.equals(r.get("raising")) : !"free".equals(scope) || !Boolean.TRUE.equals(r.get("raising")))
                .collect(Collectors.toList());
        return FarmFulfillPage.m("counts", FarmFulfillPage.m("all", rows.size(), "raising", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("raising"))).count(),
                "free", rows.stream().filter(r -> !Boolean.TRUE.equals(r.get("raising"))).count()), "list", list);
    }

    // ---------- 农资代办 ----------

    public Map<String, Object> supplyTaskList(String tab) {
        FarmStewardDO st = currentSteward();
        boolean wantDone = "done".equals(tab);
        Map<Long, FarmAdoptionDO> ads = myAdoptions(st).stream().collect(Collectors.toMap(FarmAdoptionDO::getId, a -> a, (a, b) -> a));
        List<FarmSupplyOrderDO> orders = ads.isEmpty() ? new ArrayList<>() : supplyOrderMapper.selectList(new LambdaQueryWrapper<FarmSupplyOrderDO>()
                .in(FarmSupplyOrderDO::getAdoptionId, ads.keySet()).orderByDesc(FarmSupplyOrderDO::getCreateTime));
        Map<Long, FarmSupplyDO> supplies = orders.isEmpty() ? new HashMap<>() : supplyMapper.selectBatchIds(
                orders.stream().map(FarmSupplyOrderDO::getSpuId).distinct().collect(Collectors.toList())).stream().collect(Collectors.toMap(FarmSupplyDO::getId, s -> s));
        List<Map<String, Object>> all = new ArrayList<>();
        for (FarmSupplyOrderDO o : orders) {
            FarmSupplyDO spu = supplies.get(o.getSpuId());
            String cat = spu == null || spu.getCat() == null ? "" : spu.getCat();
            if ("ins".equals(cat) || "svc".equals(cat)) continue;
            FarmAdoptionDO ad = ads.get(o.getAdoptionId());
            all.add(FarmFulfillPage.m("id", o.getId(), "no", o.getNo(), "assetCode", assetCode(o.getAssetId()), "memberName", memberName(o.getMemberId()),
                    "supplyName", (spu == null ? "" : spu.getName()) + (o.getQty() != null && o.getQty() > 1 ? " ×" + o.getQty() : ""),
                    "cat", cat, "batchNo", o.getBatchNo(), "kind", SUPPLY_KIND.getOrDefault(cat, "代办"),
                    "careModeName", FarmViewLookup.careModeName(ad.getCareMode()), "hint", SUPPLY_HINT.getOrDefault(cat, ""),
                    "done", is(o.getTraceState(), 10), "usedAt", o.getUsedAt(), "dosage", o.getDosage(), "createTime", o.getCreateTime()));
        }
        return FarmFulfillPage.m("counts", FarmFulfillPage.m("todo", all.stream().filter(v -> !Boolean.TRUE.equals(v.get("done"))).count(),
                        "done", all.stream().filter(v -> Boolean.TRUE.equals(v.get("done"))).count()),
                "list", all.stream().filter(v -> wantDone == Boolean.TRUE.equals(v.get("done"))).collect(Collectors.toList()));
    }

    /** 登记施用 / 安装，写入投入品台账（口径同后台 supply-order/trace，只能登记自己负责的认养） */
    @Transactional(rollbackFor = Exception.class)
    public void supplyTaskApply(Long id, String dosage, Long usedAtMs) {
        FarmStewardDO st = currentSteward();
        FarmSupplyOrderDO o = id == null ? null : supplyOrderMapper.selectById(id);
        if (o == null) throw exception(NOT_FOUND, "农资订单");
        assertMine(st, o.getAdoptionId());
        if (StrUtil.isBlank(dosage)) throw exception(BAD_REQUEST, "请填写用量");
        if (is(o.getTraceState(), 10)) throw exception(STATE_CHANGED, "已经登记过了");
        LocalDateTime usedAt = usedAtMs == null || usedAtMs <= 0 ? LocalDateTime.now()
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(usedAtMs), java.time.ZoneId.systemDefault());
        o.setTraceState(10);
        o.setUsedAt(usedAt);
        o.setDosage(dosage.trim());
        supplyOrderMapper.updateById(o);
        FarmSupplyDO spu = o.getSpuId() == null ? null : supplyMapper.selectById(o.getSpuId());
        FarmInputRecordDO rec = new FarmInputRecordDO();
        rec.setAssetId(o.getAssetId());
        rec.setSource("farm");
        rec.setName(spu == null ? "" : spu.getName());
        rec.setBatchNo(o.getBatchNo());
        rec.setUsedAt(usedAt);
        rec.setDosage(o.getDosage());
        rec.setOperator(lookup.userNames(List.of(st.getUserId())).getOrDefault(st.getUserId(), ""));
        inputRecordMapper.insert(rec);
    }

    // ---------- 异常上报 ----------

    public List<Map<String, Object>> alarmList() {
        FarmStewardDO st = currentSteward();
        List<Map<String, Object>> out = new ArrayList<>();
        for (FarmStewardAlarmDO a : alarmMapper.selectList(new LambdaQueryWrapper<FarmStewardAlarmDO>()
                .eq(FarmStewardAlarmDO::getStewardId, st.getUserId()).orderByDesc(FarmStewardAlarmDO::getCreateTime).orderByDesc(FarmStewardAlarmDO::getId))) {
            out.add(FarmFulfillPage.m("id", a.getId(), "type", a.getType(), "level", a.getLevel(), "assetId", a.getAssetId(),
                    "assetCode", a.getAssetId() == null ? "" : assetCode(a.getAssetId()), "title", a.getTitle(), "content", a.getContent(), "qty", a.getQty(),
                    "photos", a.getPhotos() == null ? new ArrayList<>() : a.getPhotos(), "insuranceReported", Boolean.TRUE.equals(a.getInsuranceReported()),
                    "status", a.getStatus(), "createTime", a.getCreateTime()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public Long alarmCreate(Map<String, Object> b) {
        FarmStewardDO st = currentSteward();
        String type = b.get("type") == null ? "" : String.valueOf(b.get("type"));
        if (!ALARM_TYPES.contains(type)) throw exception(BAD_REQUEST, "请选择异常类型");
        String content = b.get("content") == null ? "" : String.valueOf(b.get("content")).trim();
        if (content.isEmpty()) throw exception(BAD_REQUEST, "请填写情况说明");
        List<String> photos = new ArrayList<>();
        if (b.get("photos") instanceof List) for (Object p : (List<Object>) b.get("photos")) if (p != null && !String.valueOf(p).isEmpty()) photos.add(String.valueOf(p));
        if (!"设备异常".equals(type) && photos.isEmpty()) throw exception(BAD_REQUEST, "损耗、灾害、病虫害必须留现场影像");
        if (photos.size() > 9) throw exception(BAD_REQUEST, "最多上传 9 张照片");
        Long assetId = null;
        if (b.get("assetId") != null && !String.valueOf(b.get("assetId")).isEmpty()) {
            FarmAssetDO a = assetMapper.selectById(Long.valueOf(String.valueOf(b.get("assetId"))));
            if (a == null || st.getBaseId() == null || !st.getBaseId().equals(a.getBaseId())) throw exception(BAD_REQUEST, "请选择本基地的标的");
            assetId = a.getId();
        }
        Integer qty = null;
        if (b.get("qty") != null && !String.valueOf(b.get("qty")).isEmpty()) qty = (int) Double.parseDouble(String.valueOf(b.get("qty")));
        if ("损耗登记".equals(type) && (qty == null || qty <= 0)) throw exception(BAD_REQUEST, "请填写损耗数量");
        FarmStewardAlarmDO a = new FarmStewardAlarmDO();
        a.setStewardId(st.getUserId());
        a.setType(type);
        a.setLevel("气象灾害".equals(type) || "病虫害爆发".equals(type) ? "高" : "中");
        a.setAssetId(assetId);
        a.setTitle(type + (assetId == null ? "" : " · " + assetCode(assetId)));
        a.setContent(content);
        a.setQty(qty);
        a.setPhotos(photos);
        a.setInsuranceReported("损耗登记".equals(type) || "气象灾害".equals(type));
        a.setStatus(0);
        alarmMapper.insert(a);
        return a.getId();
    }

    // ---------- 消息 / 聊天 ----------

    private void pushMessage(Long stewardUserId, Long memberId, Long assetId, String sender, String content) {
        FarmStewardMessageDO m = new FarmStewardMessageDO();
        m.setStewardId(stewardUserId);
        m.setMemberId(memberId);
        m.setAssetId(assetId);
        m.setSender(sender);
        m.setContent(content);
        messageMapper.insert(m);
    }

    public Map<String, Object> conversations() {
        FarmStewardDO st = currentSteward();
        Map<Long, FarmAdoptionDO> byMember = new LinkedHashMap<>();
        for (FarmAdoptionDO a : myAdoptions(st)) if (is(a.getStatus(), 10)) byMember.putIfAbsent(a.getMemberId(), a);
        List<Map<String, Object>> list = new ArrayList<>();
        for (FarmAdoptionDO a : byMember.values()) {
            List<FarmStewardMessageDO> last = messageMapper.selectList(new LambdaQueryWrapper<FarmStewardMessageDO>()
                    .eq(FarmStewardMessageDO::getStewardId, st.getUserId()).eq(FarmStewardMessageDO::getMemberId, a.getMemberId())
                    .orderByDesc(FarmStewardMessageDO::getCreateTime).orderByDesc(FarmStewardMessageDO::getId).last("LIMIT 1"));
            list.add(FarmFulfillPage.m("memberId", a.getMemberId(), "memberName", memberName(a.getMemberId()), "assetCode", assetCode(a.getAssetId()),
                    "last", last.isEmpty() ? "" : last.get(0).getContent(), "lastTime", last.isEmpty() ? null : last.get(0).getCreateTime(), "unread", 0));
        }
        list.sort(Comparator.comparing((Map<String, Object> v) -> (LocalDateTime) v.get("lastTime"), Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())).reversed());
        return FarmFulfillPage.m("list", list, "notices", 0);
    }

    public Map<String, Object> messageList(Long memberId) {
        FarmStewardDO st = currentSteward();
        FarmAdoptionDO ad = myAdoptions(st).stream().filter(a -> a.getMemberId().equals(memberId)).findFirst()
                .orElseThrow(() -> exception(NOT_MINE, "只能和自己负责的认养人聊天"));
        FarmMemberDO m = memberMapper.selectById(memberId);
        List<Map<String, Object>> list = messageMapper.selectList(new LambdaQueryWrapper<FarmStewardMessageDO>()
                        .eq(FarmStewardMessageDO::getStewardId, st.getUserId()).eq(FarmStewardMessageDO::getMemberId, memberId)
                        .orderByAsc(FarmStewardMessageDO::getCreateTime).orderByAsc(FarmStewardMessageDO::getId)).stream()
                .map(x -> FarmFulfillPage.m("id", x.getId(), "from", x.getSender(), "content", x.getContent(), "createTime", x.getCreateTime()))
                .collect(Collectors.toList());
        return FarmFulfillPage.m("member", FarmFulfillPage.m("id", memberId, "name", m == null ? "" : m.getNickname(),
                "mobile", m == null || m.getMobile() == null ? "" : m.getMobile().replaceAll("^(\\d{3})\\d{4}(\\d{4})$", "$1****$2"),
                "assetCode", assetCode(ad.getAssetId())), "list", list);
    }

    public void messageSend(Long memberId, String content) {
        FarmStewardDO st = currentSteward();
        FarmAdoptionDO ad = myAdoptions(st).stream().filter(a -> a.getMemberId().equals(memberId) && is(a.getStatus(), 10)).findFirst()
                .orElseThrow(() -> exception(NOT_MINE, "只能和自己负责的认养人聊天"));
        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) throw exception(BAD_REQUEST, "请输入内容");
        if (c.length() > 500) throw exception(BAD_REQUEST, "一条消息最多 500 字");
        pushMessage(st.getUserId(), memberId, ad.getAssetId(), "steward", c);
    }

    // ---------- 绩效 ----------

    public Map<String, Object> performance() {
        FarmStewardDO st = currentSteward();
        Long uid = st.getUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<FarmAdoptionDO> mineAd = myAdoptions(st).stream().filter(a -> is(a.getStatus(), 10)).collect(Collectors.toList());
        List<FarmTaskDO> due = taskMapper.selectList(new LambdaQueryWrapper<FarmTaskDO>().eq(FarmTaskDO::getStewardId, uid).eq(FarmTaskDO::getOwner, "steward")
                .ge(FarmTaskDO::getDeadline, monthStart).le(FarmTaskDO::getDeadline, now));
        long inTime = due.stream().filter(t -> is(t.getStatus(), 10) && t.getFinishTime() != null && !t.getFinishTime().isAfter(t.getDeadline())).count();
        List<FarmRecordDO> records = recordMapper.selectList(new LambdaQueryWrapper<FarmRecordDO>().eq(FarmRecordDO::getStewardId, uid).ge(FarmRecordDO::getCreateTime, monthStart));
        double promised = mineAd.stream().mapToDouble(a -> a.getPromisedJin() == null ? 0 : a.getPromisedJin().doubleValue()).sum();
        double harvested = mineAd.stream().mapToDouble(a -> a.getHarvestedJin() == null ? 0 : a.getHarvestedJin().doubleValue()).sum();
        FarmBaseDO base = st.getBaseId() == null ? null : baseMapper.selectById(st.getBaseId());
        // 响应时长、满意度、绩效分：系统没有这类数据，返回 null，不编造
        return FarmFulfillPage.m("stewardName", lookup.userNames(List.of(uid)).getOrDefault(uid, ""), "baseName", base == null ? "" : base.getName(),
                "month", LocalDate.now().toString().substring(0, 7), "assetCount", mineAd.size(),
                "memberCount", mineAd.stream().map(FarmAdoptionDO::getMemberId).distinct().count(), "monthCheckins", records.size(),
                "completionRate", due.isEmpty() ? null : Math.round(inTime * 100.0 / due.size()),
                "passRate", records.isEmpty() ? null : Math.round(records.stream().filter(r -> is(r.getStatus(), 10)).count() * 100.0 / records.size()),
                "outputRate", promised > 0 ? Math.round(harvested / promised * 100) : null,
                "responseMinutes", null, "satisfaction", null, "score", null);
    }
}
