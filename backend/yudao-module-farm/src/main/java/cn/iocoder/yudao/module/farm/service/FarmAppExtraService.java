package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAppExtraDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmShipmentDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAppExtraMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmHarvestMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmShipmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.ORDER_STATUS_CHANGED;

/**
 * C 端第二期补页：搜索、基地、收藏、地址、消息、农事记录详情、质检报告、物流详情、转赠、评价、预约到访、在线管家。
 * mock：mock-server/src/routes/farm-app-extra.ts（逐接口对应，返回 Map 以保持与 mock 相同的 JSON 形状）。
 * 租户隔离由芋道拦截器自动追加 tenant_id；会员小数据存 farm_app_extra（按 kind 区分）。
 */
@Service
public class FarmAppExtraService {

    private static final Map<String, String> CATEGORY = Map.of("plot", "土地认养", "animal", "家禽认养", "tree", "果树认养");
    private static final String STEWARD_REPLY = "收到，我马上帮您看一下，稍后在这里回复您。（演示：管家端接入后由真人回复）";

    @Autowired private FarmAppSupport support;
    @Autowired private FarmAppExtraMapper extraMapper;
    @Autowired private FarmBaseMapper baseMapper;
    @Autowired private FarmProductMapper productMapper;
    @Autowired private FarmArticleMapper articleMapper;
    @Autowired private FarmOrderMapper orderMapper;
    @Autowired private FarmAdoptionMapper adoptionMapper;
    @Autowired private FarmRecordMapper recordMapper;
    @Autowired private FarmAssetMapper assetMapper;
    @Autowired private FarmShipmentMapper shipmentMapper;
    @Autowired private FarmHarvestMapper harvestMapper;
    @Autowired private FarmViewLookup lookup;
    @Autowired private FarmNoGenerator noGenerator;

    // ---------- 搜索 / 商城 ----------
    public List<String> hotWords() {
        return List.of("一分地", "跑山鸡", "果树", "水稻", "有机蔬菜", "农资");
    }

    public Map<String, Object> search(String keyword, String type, String sort) {
        String kw = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<FarmProductDO> w = new LambdaQueryWrapper<FarmProductDO>().eq(FarmProductDO::getStatus, 0);
        if (type != null && !type.isEmpty()) w.eq(FarmProductDO::getAssetType, type);
        List<FarmProductDO> ps = productMapper.selectList(w);
        Map<Long, FarmBaseDO> bases = baseMapper.selectList(new LambdaQueryWrapper<FarmBaseDO>().eq(FarmBaseDO::getStatus, 0))
                .stream().collect(Collectors.toMap(FarmBaseDO::getId, b -> b));
        List<Map<String, Object>> products = new ArrayList<>();
        for (FarmProductDO p : ps) {
            FarmBaseDO b = bases.get(p.getBaseId());
            if (!kw.isEmpty() && !contains(p.getTitle(), kw) && !contains(p.getDescription(), kw) && !(b != null && contains(b.getName(), kw))) continue;
            products.add(productCard(p, b));
        }
        if ("sold".equals(sort)) products.sort((a, c) -> Integer.compare(num(c.get("sold")), num(a.get("sold"))));
        else if ("price".equals(sort)) products.sort(Comparator.comparingLong(a -> ((Number) a.get("minPrice")).longValue()));
        List<Map<String, Object>> baseCards = new ArrayList<>();
        if (type == null || type.isEmpty()) {
            for (FarmBaseDO b : bases.values()) {
                if (kw.isEmpty() || contains(b.getName(), kw) || contains(b.getAddress(), kw) || contains(b.getCropSummary(), kw)) baseCards.add(baseCard(b));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("products", products);
        out.put("bases", baseCards);
        return out;
    }

    // ---------- 基地 ----------
    public List<Map<String, Object>> baseList() {
        return baseMapper.selectList(new LambdaQueryWrapper<FarmBaseDO>().eq(FarmBaseDO::getStatus, 0))
                .stream().map(this::baseCard).collect(Collectors.toList());
    }

    public Map<String, Object> getBase(Long id) {
        FarmBaseDO b = baseMapper.selectById(id);
        if (b == null || b.getStatus() == null || b.getStatus() != 0) throw exception(NOT_FOUND, "基地");
        Map<String, Object> v = baseCard(b);
        v.put("lng", b.getLng());
        v.put("lat", b.getLat());
        v.put("products", productMapper.selectList(new LambdaQueryWrapper<FarmProductDO>()
                .eq(FarmProductDO::getBaseId, id).eq(FarmProductDO::getStatus, 0)).stream().map(p -> productCard(p, b)).collect(Collectors.toList()));
        v.put("certs", List.of(cert("基地备案编号", b.getCertNo()), cert("土地权属证明", "演示数据"), cert("投入品合规承诺书", "演示数据")));
        v.put("news", articleMapper.selectList(new LambdaQueryWrapper<FarmArticleDO>().eq(FarmArticleDO::getStatus, 0)
                .orderByDesc(FarmArticleDO::getCreateTime).last("LIMIT 3")).stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("introduction", a.getIntroduction());
            m.put("picUrl", a.getPicUrl());
            m.put("createTime", FarmAppSupport.toMillis(a.getCreateTime()));
            return m;
        }).collect(Collectors.toList()));
        v.put("reviews", reviewsOfBase(id, 5));
        v.put("demo", true);
        return v;
    }

    // ---------- 收藏 ----------
    public List<Map<String, Object>> favoriteList(String type) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FarmAppExtraDO f : mine("favorite")) {
            String t = str(f.getPayload(), "type");
            if (type != null && !type.isEmpty() && !type.equals(t)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("type", t);
            m.put("targetId", f.getTargetId());
            m.put("createTime", FarmAppSupport.toMillis(f.getCreateTime()));
            if ("product".equals(t)) {
                FarmProductDO p = productMapper.selectById(f.getTargetId());
                if (p == null) continue;
                m.put("product", productCard(p, baseMapper.selectById(p.getBaseId())));
            } else {
                FarmBaseDO b = baseMapper.selectById(f.getTargetId());
                if (b == null) continue;
                m.put("base", baseCard(b));
            }
            out.add(m);
        }
        return out;
    }

    public boolean favoriteCheck(String type, Long targetId) {
        return findFavorite(type, targetId) != null;
    }

    /** 切换收藏，返回切换后的状态 */
    public boolean favoriteToggle(String type, Long targetId) {
        if (!"product".equals(type) && !"base".equals(type)) throw exception(BAD_REQUEST, "收藏类型不对");
        boolean exists = "product".equals(type) ? productMapper.selectById(targetId) != null : baseMapper.selectById(targetId) != null;
        if (!exists) throw exception(NOT_FOUND, "product".equals(type) ? "商品" : "基地");
        FarmAppExtraDO f = findFavorite(type, targetId);
        if (f != null) {
            extraMapper.deleteById(f.getId());
            return false;
        }
        f = new FarmAppExtraDO();
        f.setMemberId(support.memberId());
        f.setKind("favorite");
        f.setTargetId(targetId);
        f.setPayload(new HashMap<>(Map.of("type", type)));
        extraMapper.insert(f);
        return true;
    }

    // ---------- 收货地址 ----------
    public List<Map<String, Object>> addressList() {
        List<Map<String, Object>> out = mine("address").stream().map(this::addressView).collect(Collectors.toList());
        FarmMemberDO m = support.member();
        if (out.isEmpty() && m.getAddress() != null && !m.getAddress().isEmpty()) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", 0);
            a.put("name", m.getNickname());
            a.put("mobile", m.getMobile());
            a.put("detail", m.getAddress());
            a.put("isDefault", true);
            out.add(a);
        }
        out.sort((x, y) -> Boolean.compare(Boolean.TRUE.equals(y.get("isDefault")), Boolean.TRUE.equals(x.get("isDefault"))));
        return out;
    }

    public Long addressSave(Map<String, Object> b) {
        String name = str(b, "name").trim();
        String mobile = str(b, "mobile").trim();
        String detail = str(b, "detail").trim();
        if (name.isEmpty()) throw exception(BAD_REQUEST, "请填写收货人");
        if (!mobile.matches("^1\\d{10}$")) throw exception(BAD_REQUEST, "手机号格式不对");
        if (detail.isEmpty()) throw exception(BAD_REQUEST, "请填写详细地址");
        List<FarmAppExtraDO> all = mine("address");
        boolean isDefault = Boolean.TRUE.equals(b.get("isDefault")) || all.isEmpty();
        if (isDefault) {
            for (FarmAppExtraDO a : all) {
                a.getPayload().put("isDefault", false);
                extraMapper.updateById(a);
            }
        }
        Long id = b.get("id") instanceof Number ? ((Number) b.get("id")).longValue() : null;
        FarmAppExtraDO a = id == null || id == 0 ? null : all.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("mobile", mobile);
        payload.put("detail", detail);
        payload.put("isDefault", isDefault);
        if (a != null) {
            a.setPayload(payload);
            extraMapper.updateById(a);
            return a.getId();
        }
        a = new FarmAppExtraDO();
        a.setMemberId(support.memberId());
        a.setKind("address");
        a.setPayload(payload);
        extraMapper.insert(a);
        return a.getId();
    }

    public boolean addressDelete(Long id) {
        List<FarmAppExtraDO> all = mine("address");
        FarmAppExtraDO a = all.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow(() -> exception(NOT_FOUND, "地址"));
        extraMapper.deleteById(a.getId());
        if (Boolean.TRUE.equals(a.getPayload().get("isDefault"))) {
            all.stream().filter(x -> !x.getId().equals(id)).findFirst().ifPresent(r -> {
                r.getPayload().put("isDefault", true);
                extraMapper.updateById(r);
            });
        }
        return true;
    }

    // ---------- 消息中心：由订单、配送、农事记录、管家回复、转赠派生 ----------
    public Map<String, Object> messageList(String type) {
        List<Map<String, Object>> all = messages();
        Set<String> read = mine("msg_read").stream().map(x -> str(x.getPayload(), "key")).collect(Collectors.toSet());
        all.forEach(m -> m.put("read", read.contains(String.valueOf(m.get("key")))));
        long unread = all.stream().filter(m -> !Boolean.TRUE.equals(m.get("read"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unread", unread);
        out.put("list", all.stream().filter(m -> type == null || type.isEmpty() || type.equals(m.get("type"))).collect(Collectors.toList()));
        return out;
    }

    public boolean messageRead(String key) {
        Set<String> read = mine("msg_read").stream().map(x -> str(x.getPayload(), "key")).collect(Collectors.toSet());
        for (Map<String, Object> m : messages()) {
            String k = String.valueOf(m.get("key"));
            if ((key != null && !key.isEmpty() && !key.equals(k)) || read.contains(k)) continue;
            FarmAppExtraDO r = new FarmAppExtraDO();
            r.setMemberId(support.memberId());
            r.setKind("msg_read");
            r.setPayload(new HashMap<>(Map.of("key", k)));
            extraMapper.insert(r);
        }
        return true;
    }

    private List<Map<String, Object>> messages() {
        Long mid = support.memberId();
        List<Map<String, Object>> out = new ArrayList<>();
        List<FarmAdoptionDO> ads = adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>().eq(FarmAdoptionDO::getMemberId, mid));
        for (FarmOrderDO o : orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>().eq(FarmOrderDO::getMemberId, mid).ge(FarmOrderDO::getStatus, 10))) {
            out.add(msg("o" + o.getId(), "system", "认养成功", "订单 " + o.getNo() + " 已支付，合同已归档，管家会尽快联系您。",
                    FarmAppSupport.toMillis(o.getPayTime() != null ? o.getPayTime() : o.getCreateTime()), "/pages/farm/contract/list"));
        }
        for (FarmShipmentDO s : shipmentMapper.selectList(new LambdaQueryWrapper<FarmShipmentDO>().eq(FarmShipmentDO::getMemberId, mid))) {
            Map<String, Object> last = s.getTracks() == null || s.getTracks().isEmpty() ? null : s.getTracks().get(s.getTracks().size() - 1);
            long time = last != null && last.get("time") instanceof Number ? ((Number) last.get("time")).longValue() : FarmAppSupport.toMillis(s.getCreateTime());
            out.add(msg("s" + s.getId(), "logistics", s.getStatus() != null && s.getStatus() == 20 ? "配送已签收" : "配送进度更新",
                    s.getCarrier() + " " + s.getLogisticsNo() + "：" + (last == null ? "待发货" : last.get("content")),
                    time, "/pages/farm/logistics/detail?id=" + s.getId()));
        }
        if (!ads.isEmpty()) {
            List<Long> adIds = ads.stream().map(FarmAdoptionDO::getId).collect(Collectors.toList());
            for (FarmRecordDO r : recordMapper.selectList(new LambdaQueryWrapper<FarmRecordDO>().in(FarmRecordDO::getAdoptionId, adIds).eq(FarmRecordDO::getStatus, 10))) {
                out.add(msg("r" + r.getId(), "farming", "新的农事记录", r.getTitle(), FarmAppSupport.toMillis(r.getCreateTime()), "/pages/farm/record/detail?id=" + r.getId()));
            }
        }
        for (FarmAppExtraDO c : mine("chat")) {
            if ("steward".equals(str(c.getPayload(), "from"))) {
                out.add(msg("c" + c.getId(), "interact", "管家回复了您", str(c.getPayload(), "content"), FarmAppSupport.toMillis(c.getCreateTime()), "/pages/farm/service/chat"));
            }
        }
        for (FarmAppExtraDO t : mine("transfer")) {
            String mob = str(t.getPayload(), "toMobile").replaceAll("^(\\d{3})\\d{4}(\\d{4})$", "$1****$2");
            out.add(msg("t" + t.getId(), "interact", "转赠申请已提交", "转赠给 " + mob + "，等待对方确认", FarmAppSupport.toMillis(t.getCreateTime()), "/pages/farm/transfer/index?adoptionId=" + t.getTargetId()));
        }
        out.sort((a, b) -> Long.compare((Long) b.get("createTime"), (Long) a.get("createTime")));
        return out;
    }

    // ---------- 农事记录详情 ----------
    public Map<String, Object> getRecord(Long id) {
        FarmRecordDO r = recordMapper.selectById(id);
        FarmAdoptionDO ad = r == null ? null : support.myAdoption(r.getAdoptionId());
        if (r == null || ad == null || r.getStatus() == null || r.getStatus() != 10) throw exception(NOT_FOUND, "农事记录");
        FarmAssetDO asset = assetMapper.selectById(r.getAssetId());
        Map<Long, String> names = lookup.userNames(List.of(r.getStewardId()));
        Map<String, Object> checks = r.getChecks() == null ? Map.of() : r.getChecks();
        boolean ok = !Boolean.FALSE.equals(checks.get("exifTime")) && !Boolean.FALSE.equals(checks.get("gpsFence")) && !Boolean.FALSE.equals(checks.get("duplicate"));
        Map<String, String> kinds = Map.of("farming", "农事", "harvest", "采收", "service", "代管服务");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("adoptionId", r.getAdoptionId());
        v.put("assetCode", asset == null ? "" : asset.getCode());
        v.put("title", r.getTitle());
        v.put("content", r.getContent());
        v.put("kindName", kinds.getOrDefault(r.getKind(), ""));
        v.put("photos", r.getPhotos());
        v.put("stewardName", names.getOrDefault(r.getStewardId(), ""));
        v.put("env", r.getEnv());
        v.put("lng", r.getLng());
        v.put("lat", r.getLat());
        v.put("createTime", FarmAppSupport.toMillis(r.getCreateTime()));
        v.put("evidenceNo", "CZ" + String.format("%08d", r.getId()));
        v.put("checks", checks);
        v.put("machineCheck", ok ? "ok" : "suspect");
        v.put("workHours", null);
        v.put("demo", true);
        return v;
    }

    // ---------- 质检报告（演示数据） ----------
    public Map<String, Object> getReport(Long adoptionId) {
        FarmAdoptionDO ad = support.myAdoption(adoptionId);
        if (ad == null) throw exception(NOT_FOUND, "认养");
        FarmAssetDO asset = assetMapper.selectById(ad.getAssetId());
        String crop = "农产品";
        if (asset != null && asset.getSpecs() != null) {
            Object c = asset.getSpecs().get("crop") != null ? asset.getSpecs().get("crop") : asset.getSpecs().get("breed");
            if (c != null) crop = String.valueOf(c);
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("adoptionId", ad.getId());
        v.put("assetCode", asset == null ? "" : asset.getCode());
        v.put("crop", crop);
        v.put("reportNo", "JC" + String.format("%06d", ad.getId()));
        v.put("org", "演示检测机构");
        v.put("sampleDate", FarmAppSupport.toMillis(ad.getStartAt()));
        v.put("conclusion", "合格");
        v.put("items", List.of(item("毒死蜱", "≤ 0.01 mg/kg", "未检出"), item("氯氰菊酯", "≤ 0.5 mg/kg", "未检出"),
                item("铅（以 Pb 计）", "≤ 0.1 mg/kg", "0.02 mg/kg"), item("镉（以 Cd 计）", "≤ 0.05 mg/kg", "0.01 mg/kg")));
        v.put("demo", true);
        return v;
    }

    // ---------- 物流详情 ----------
    public Map<String, Object> getLogistics(Long id) {
        FarmShipmentDO s = shipmentMapper.selectById(id);
        if (s == null || !support.memberId().equals(s.getMemberId())) throw exception(NOT_FOUND, "配送单");
        FarmHarvestDO h = harvestMapper.selectById(s.getHarvestId());
        long nth = shipmentMapper.selectCount(new LambdaQueryWrapper<FarmShipmentDO>()
                .eq(FarmShipmentDO::getAdoptionId, s.getAdoptionId()).le(FarmShipmentDO::getId, s.getId()));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", s.getId());
        v.put("no", s.getNo());
        v.put("nth", nth);
        v.put("status", s.getStatus());
        v.put("carrier", s.getCarrier());
        v.put("logisticsNo", s.getLogisticsNo());
        v.put("coldChain", s.getColdChain());
        v.put("receiverName", s.getReceiverName());
        v.put("receiverMobile", s.getReceiverMobile());
        v.put("address", s.getAddress());
        v.put("weightJin", s.getWeightJin());
        v.put("tracks", s.getTracks());
        Map<String, Object> it = new LinkedHashMap<>();
        it.put("name", h == null ? "农产品" : h.getCrop());
        it.put("weightJin", s.getWeightJin());
        v.put("items", List.of(it));
        FarmAdoptionDO ad = adoptionMapper.selectById(s.getAdoptionId());
        Map<String, Object> steward = null;
        if (ad != null && ad.getStewardId() != null) {
            String name = lookup.userNames(List.of(ad.getStewardId())).get(ad.getStewardId());
            if (name != null) {
                steward = new LinkedHashMap<>();
                steward.put("name", name);
                steward.put("mobile", "");
            }
        }
        v.put("steward", steward);
        return v;
    }

    // ---------- 转赠（演示：只记录申请，不改归属） ----------
    public List<Map<String, Object>> transferList() {
        return mine("transfer").stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>(t.getPayload());
            m.put("id", t.getId());
            m.put("adoptionId", t.getTargetId());
            m.put("createTime", FarmAppSupport.toMillis(t.getCreateTime()));
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> transferCreate(Map<String, Object> b) {
        FarmAdoptionDO ad = support.myAdoption(b.get("adoptionId") instanceof Number ? ((Number) b.get("adoptionId")).longValue() : null);
        if (ad == null) throw exception(NOT_FOUND, "认养");
        if (ad.getStatus() == null || ad.getStatus() != 10) throw exception(ORDER_STATUS_CHANGED);
        String to = str(b, "toMobile");
        if (!to.matches("^1\\d{10}$")) throw exception(BAD_REQUEST, "受赠人手机号格式不对");
        if (to.equals(support.member().getMobile())) throw exception(BAD_REQUEST, "不能转赠给自己");
        boolean pending = mine("transfer").stream().anyMatch(t -> ad.getId().equals(t.getTargetId()) && Integer.valueOf(0).equals(t.getPayload().get("status")));
        if (pending) throw exception(ORDER_STATUS_CHANGED);
        String no = noGenerator.next("ZZ");
        Map<String, Object> p = new HashMap<>();
        p.put("no", no);
        p.put("toMobile", to);
        p.put("scope", "right".equals(b.get("scope")) ? "right" : "all");
        p.put("message", str(b, "message"));
        p.put("status", 0);
        FarmAppExtraDO t = new FarmAppExtraDO();
        t.setMemberId(support.memberId());
        t.setKind("transfer");
        t.setTargetId(ad.getId());
        t.setPayload(p);
        extraMapper.insert(t);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", t.getId());
        out.put("no", no);
        return out;
    }

    // ---------- 评价 ----------
    public List<Map<String, Object>> reviewList(Long productId) {
        return reviewViews(extraMapper.selectList(new LambdaQueryWrapper<FarmAppExtraDO>().eq(FarmAppExtraDO::getKind, "review")
                .eq(productId != null, FarmAppExtraDO::getTargetId, productId).orderByDesc(FarmAppExtraDO::getCreateTime)));
    }

    public List<Map<String, Object>> reviewMine() {
        return reviewViews(mine("review"));
    }

    public Long reviewCreate(Map<String, Object> b) {
        FarmAdoptionDO ad = support.myAdoption(b.get("adoptionId") instanceof Number ? ((Number) b.get("adoptionId")).longValue() : null);
        if (ad == null) throw exception(NOT_FOUND, "认养");
        int score = b.get("score") instanceof Number ? ((Number) b.get("score")).intValue() : 0;
        if (score < 1 || score > 5) throw exception(BAD_REQUEST, "请打分");
        boolean done = mine("review").stream().anyMatch(r -> r.getPayload().get("adoptionId") instanceof Number
                && ad.getId().equals(((Number) r.getPayload().get("adoptionId")).longValue()));
        if (done) throw exception(ORDER_STATUS_CHANGED);
        Map<?, ?> d = b.get("dims") instanceof Map ? (Map<?, ?>) b.get("dims") : Map.of();
        Map<String, Object> p = new HashMap<>();
        p.put("adoptionId", ad.getId());
        p.put("score", score);
        p.put("freshness", dim(d.get("freshness"), score));
        p.put("service", dim(d.get("service"), score));
        p.put("logistics", dim(d.get("logistics"), score));
        String content = str(b, "content");
        p.put("content", content.length() > 500 ? content.substring(0, 500) : content);
        String nick = support.member().getNickname();
        p.put("memberName", (nick == null || nick.isEmpty() ? "认" : nick.substring(0, 1)) + "**");
        FarmAppExtraDO r = new FarmAppExtraDO();
        r.setMemberId(support.memberId());
        r.setKind("review");
        r.setTargetId(ad.getProductId());
        r.setPayload(p);
        extraMapper.insert(r);
        return r.getId();
    }

    // ---------- 预约到访 ----------
    public List<Map<String, Object>> visitList() {
        return mine("visit").stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>(v.getPayload());
            m.put("id", v.getId());
            m.put("baseId", v.getTargetId());
            m.put("createTime", FarmAppSupport.toMillis(v.getCreateTime()));
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> visitCreate(Map<String, Object> b) {
        FarmBaseDO base = b.get("baseId") instanceof Number ? baseMapper.selectById(((Number) b.get("baseId")).longValue()) : null;
        if (base == null) throw exception(NOT_FOUND, "基地");
        LocalDate date;
        try {
            date = LocalDate.parse(str(b, "date"));
        } catch (Exception e) {
            throw exception(BAD_REQUEST, "请选择到访日期");
        }
        if (date.isBefore(LocalDate.now())) throw exception(BAD_REQUEST, "到访日期不能早于今天");
        int people = b.get("people") instanceof Number ? ((Number) b.get("people")).intValue() : 0;
        if (people < 1 || people > 20) throw exception(BAD_REQUEST, "人数需在 1 到 20 之间");
        String no = noGenerator.next("FW");
        Map<String, Object> p = new HashMap<>();
        p.put("no", no);
        p.put("baseName", base.getName());
        p.put("date", date.toString());
        p.put("people", people);
        p.put("meal", b.get("meal") == null ? "none" : String.valueOf(b.get("meal")));
        p.put("items", b.get("items") instanceof List ? b.get("items") : List.of());
        p.put("remark", str(b, "remark"));
        p.put("status", 0);
        FarmAppExtraDO v = new FarmAppExtraDO();
        v.setMemberId(support.memberId());
        v.setKind("visit");
        v.setTargetId(base.getId());
        v.setPayload(p);
        extraMapper.insert(v);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", v.getId());
        out.put("no", no);
        return out;
    }

    // ---------- 在线管家（演示：自动回复） ----------
    public Map<String, Object> serviceMessages() {
        List<FarmAdoptionDO> ads = adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>()
                .eq(FarmAdoptionDO::getMemberId, support.memberId()).orderByDesc(FarmAdoptionDO::getStartAt).last("LIMIT 1"));
        Map<String, Object> steward = null;
        if (!ads.isEmpty() && ads.get(0).getStewardId() != null) {
            String name = lookup.userNames(List.of(ads.get(0).getStewardId())).get(ads.get(0).getStewardId());
            if (name != null) {
                steward = new LinkedHashMap<>();
                steward.put("name", name);
                steward.put("mobile", "");
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("steward", steward);
        out.put("list", mine("chat").stream().sorted(Comparator.comparing(FarmAppExtraDO::getId)).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("from", str(c.getPayload(), "from"));
            m.put("content", str(c.getPayload(), "content"));
            m.put("createTime", FarmAppSupport.toMillis(c.getCreateTime()));
            return m;
        }).collect(Collectors.toList()));
        return out;
    }

    public boolean serviceSend(String content) {
        String c = content == null ? "" : content.trim();
        if (c.isEmpty()) throw exception(BAD_REQUEST, "请输入内容");
        addChat("me", c.length() > 500 ? c.substring(0, 500) : c);
        addChat("steward", STEWARD_REPLY);
        return true;
    }

    // ---------- 内部工具 ----------
    private void addChat(String from, String content) {
        FarmAppExtraDO c = new FarmAppExtraDO();
        c.setMemberId(support.memberId());
        c.setKind("chat");
        Map<String, Object> p = new HashMap<>();
        p.put("from", from);
        p.put("content", content);
        c.setPayload(p);
        extraMapper.insert(c);
    }

    private List<FarmAppExtraDO> mine(String kind) {
        return extraMapper.selectList(new LambdaQueryWrapper<FarmAppExtraDO>().eq(FarmAppExtraDO::getMemberId, support.memberId())
                .eq(FarmAppExtraDO::getKind, kind).orderByDesc(FarmAppExtraDO::getId));
    }

    private FarmAppExtraDO findFavorite(String type, Long targetId) {
        return mine("favorite").stream().filter(f -> type.equals(str(f.getPayload(), "type")) && targetId.equals(f.getTargetId())).findFirst().orElse(null);
    }

    private Map<String, Object> productCard(FarmProductDO p, FarmBaseDO b) {
        long min = Long.MAX_VALUE;
        if (p.getCareModes() != null) {
            for (Map<String, Object> m : p.getCareModes()) {
                if (Boolean.TRUE.equals(m.get("enabled")) && m.get("price") instanceof Number) min = Math.min(min, ((Number) m.get("price")).longValue());
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("cover", p.getCover());
        m.put("assetType", p.getAssetType());
        m.put("categoryName", CATEGORY.getOrDefault(p.getAssetType(), ""));
        m.put("baseName", b == null ? "" : b.getName());
        m.put("minPrice", min == Long.MAX_VALUE ? 0L : min);
        m.put("sold", p.getSold() == null ? 0 : p.getSold());
        m.put("stock", p.getStock() == null ? 0 : p.getStock());
        return m;
    }

    private Map<String, Object> baseCard(FarmBaseDO b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("cover", b.getCover());
        m.put("address", b.getAddress());
        m.put("areaMu", b.getAreaMu());
        m.put("cropSummary", b.getCropSummary());
        m.put("certNo", b.getCertNo());
        List<FarmProductDO> all = productMapper.selectList(new LambdaQueryWrapper<FarmProductDO>().eq(FarmProductDO::getBaseId, b.getId()));
        m.put("productCount", all.stream().filter(p -> p.getStatus() != null && p.getStatus() == 0).count());
        List<Long> pids = all.stream().map(FarmProductDO::getId).collect(Collectors.toList());
        List<FarmAppExtraDO> rs = pids.isEmpty() ? List.of() : extraMapper.selectList(new LambdaQueryWrapper<FarmAppExtraDO>()
                .eq(FarmAppExtraDO::getKind, "review").in(FarmAppExtraDO::getTargetId, pids));
        // 没有评价时不编造评分
        m.put("rating", rs.isEmpty() ? null : Math.round(rs.stream().mapToInt(r -> ((Number) r.getPayload().get("score")).intValue()).average().orElse(0) * 10) / 10.0);
        m.put("reviewCount", rs.size());
        return m;
    }

    private List<Map<String, Object>> reviewsOfBase(Long baseId, int limit) {
        List<Long> pids = productMapper.selectList(new LambdaQueryWrapper<FarmProductDO>().eq(FarmProductDO::getBaseId, baseId))
                .stream().map(FarmProductDO::getId).collect(Collectors.toList());
        if (pids.isEmpty()) return List.of();
        return reviewViews(extraMapper.selectList(new LambdaQueryWrapper<FarmAppExtraDO>().eq(FarmAppExtraDO::getKind, "review")
                .in(FarmAppExtraDO::getTargetId, pids).orderByDesc(FarmAppExtraDO::getCreateTime).last("LIMIT " + limit)));
    }

    private List<Map<String, Object>> reviewViews(List<FarmAppExtraDO> rs) {
        return rs.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("productId", r.getTargetId());
            m.put("adoptionId", r.getPayload().get("adoptionId"));
            m.put("memberName", r.getPayload().get("memberName"));
            m.put("score", r.getPayload().get("score"));
            Map<String, Object> dims = new LinkedHashMap<>();
            dims.put("freshness", r.getPayload().get("freshness"));
            dims.put("service", r.getPayload().get("service"));
            dims.put("logistics", r.getPayload().get("logistics"));
            m.put("dims", dims);
            m.put("content", r.getPayload().get("content"));
            m.put("createTime", FarmAppSupport.toMillis(r.getCreateTime()));
            return m;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> addressView(FarmAppExtraDO a) {
        Map<String, Object> m = new LinkedHashMap<>(a.getPayload());
        m.put("id", a.getId());
        return m;
    }

    private static Map<String, Object> msg(String key, String type, String title, String content, long time, String link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("type", type);
        m.put("title", title);
        m.put("content", content);
        m.put("createTime", time);
        m.put("link", link);
        return m;
    }

    private static Map<String, Object> cert(String name, String no) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("no", no);
        return m;
    }

    private static Map<String, Object> item(String name, String limit, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("limit", limit);
        m.put("result", result);
        m.put("pass", true);
        return m;
    }

    private static int dim(Object v, int fallback) {
        int n = v instanceof Number ? ((Number) v).intValue() : 0;
        return n >= 1 && n <= 5 ? n : fallback;
    }

    private static boolean contains(String s, String kw) {
        return s != null && s.contains(kw);
    }

    private static int num(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null ? "" : String.valueOf(v);
    }
}
