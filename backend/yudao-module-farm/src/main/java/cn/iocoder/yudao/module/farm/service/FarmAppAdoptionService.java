package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderAdoptionVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentRespVO;
import cn.iocoder.yudao.module.farm.controller.app.adoption.vo.FarmAppAdoptionDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.app.adoption.vo.FarmAppCameraRespVO;
import cn.iocoder.yudao.module.farm.controller.app.adoption.vo.FarmAppTraceRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmInputRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmShipmentDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmHarvestMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmInputRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmShipmentMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * C 端「我的认养」：列表、详情、生长档案、溯源、环境数据、摄像头、收获配送（mock：farm-app.ts 146–194、271–277 行；views.ts adoptionView / adoptionDetail）。
 * 只能看自己的认养（别人的、不存在的都按 404「认养不存在」）。
 */
@Service
public class FarmAppAdoptionService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmInputRecordMapper inputRecordMapper;
    @Autowired
    private FarmHarvestMapper harvestMapper;
    @Autowired
    private FarmShipmentMapper shipmentMapper;
    @Autowired
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Autowired
    private FarmRecordService recordService;
    @Autowired
    private FarmShipmentService shipmentService;
    @Autowired
    private FarmAssetService assetService;
    @Autowired
    private FarmViewLookup lookup;
    @Autowired
    private FarmAppSupport support;
    @Autowired
    private AdminUserApi adminUserApi;

    /** mock 152 行：开始时间新的在前 */
    public List<FarmOrderAdoptionVO> list() {
        Long memberId = support.memberId();
        List<FarmAdoptionDO> list = adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>()
                .eq(FarmAdoptionDO::getMemberId, memberId)
                .orderByDesc(FarmAdoptionDO::getStartAt).orderByDesc(FarmAdoptionDO::getId));
        return views(list);
    }

    /** mock 156 行 */
    public FarmAppAdoptionDetailRespVO get(Long id) {
        FarmAdoptionDO ad = mine(id);
        FarmAppAdoptionDetailRespVO d = BeanUtils.toBean(views(List.of(ad)).get(0), FarmAppAdoptionDetailRespVO.class);
        FarmAssetDO a = ad.getAssetId() == null ? null : lookup.assets(List.of(ad.getAssetId())).get(ad.getAssetId());
        FarmProductDO p = ad.getProductId() == null ? null : lookup.products(List.of(ad.getProductId())).get(ad.getProductId());

        if (a != null) {
            Map<String, Object> specs = a.getSpecs() == null ? new HashMap<>() : a.getSpecs();
            Object crop = specs.get("crop") != null ? specs.get("crop") : specs.get("breed");
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("id", a.getId());
            asset.put("code", a.getCode());
            asset.put("name", a.getName());
            asset.put("crop", crop == null ? "" : crop);
            asset.put("areaM2", specs.get("areaM2"));
            asset.put("cameraName", a.getCameraName());
            asset.put("assetType", a.getAssetType());
            d.setAsset(asset);
        }

        AdminUserRespDTO st = ad.getStewardId() == null || ad.getStewardId() <= 0 ? null : adminUserApi.getUser(ad.getStewardId());
        if (st != null) {
            Map<String, Object> steward = new LinkedHashMap<>();
            steward.put("name", st.getNickname());
            steward.put("mobile", maskMobile(st.getMobile()));
            d.setSteward(steward);
        }

        if (p != null && p.getCareModes() != null) {
            for (Map<String, Object> m : p.getCareModes()) {
                if (m != null && ad.getCareMode() != null && ad.getCareMode().equals(m.get("mode"))) {
                    d.setCareModeInfo(m);
                    break;
                }
            }
        }
        return d;
    }

    /** mock 160 行：生长档案 = 已通过（status=10）的农事记录，新的在前 */
    public List<FarmRecordRespVO> records(Long adoptionId) {
        FarmAdoptionDO ad = mine(adoptionId);
        List<FarmRecordDO> list = recordMapper.selectList(new LambdaQueryWrapper<FarmRecordDO>()
                .eq(FarmRecordDO::getAdoptionId, ad.getId())
                .eq(FarmRecordDO::getStatus, 10)
                .orderByDesc(FarmRecordDO::getCreateTime).orderByDesc(FarmRecordDO::getId));
        return recordService.toViews(list);
    }

    /** mock 165–183 行 */
    public FarmAppTraceRespVO trace(Long adoptionId) {
        FarmAdoptionDO ad = mine(adoptionId);
        List<FarmRecordDO> records = recordMapper.selectList(new LambdaQueryWrapper<FarmRecordDO>()
                .eq(FarmRecordDO::getAdoptionId, ad.getId())
                .eq(FarmRecordDO::getStatus, 10)
                .orderByAsc(FarmRecordDO::getCreateTime).orderByAsc(FarmRecordDO::getId));
        List<FarmInputRecordDO> inputs = inputRecordMapper.selectList(new LambdaQueryWrapper<FarmInputRecordDO>()
                .eq(FarmInputRecordDO::getAssetId, ad.getAssetId())
                .orderByAsc(FarmInputRecordDO::getUsedAt).orderByAsc(FarmInputRecordDO::getId));
        List<FarmHarvestDO> harvests = harvestMapper.selectList(new LambdaQueryWrapper<FarmHarvestDO>()
                .eq(FarmHarvestDO::getAdoptionId, ad.getId()).orderByAsc(FarmHarvestDO::getId));
        List<FarmShipmentDO> shipments = shipmentMapper.selectList(new LambdaQueryWrapper<FarmShipmentDO>()
                .eq(FarmShipmentDO::getAdoptionId, ad.getId()).orderByAsc(FarmShipmentDO::getId));

        FarmRecordDO sow = null;
        for (FarmRecordDO r : records) {
            String title = r.getTitle() == null ? "" : r.getTitle();
            if (title.contains("播种") || title.contains("定植")) {
                sow = r;
                break;
            }
        }
        long untraced = supplyOrderMapper.selectCount(new LambdaQueryWrapper<FarmSupplyOrderDO>()
                .eq(FarmSupplyOrderDO::getAdoptionId, ad.getId())
                .eq(FarmSupplyOrderDO::getTraceState, 0));
        long farmInputs = inputs.stream().filter(x -> "farm".equals(x.getSource())).count();
        long memberInputs = inputs.stream().filter(x -> "member".equals(x.getSource())).count();

        List<Map<String, Object>> stages = new ArrayList<>();
        stages.add(stage("seed", "种源", ad.getStartAt() == null ? null : FarmAppSupport.toMillis(ad.getStartAt()),
                "种苗来自安吉县农业良种场，批次 ZM-2026-09（演示数据）"));
        stages.add(stage("sow", "播种", sow == null || sow.getCreateTime() == null ? null : FarmAppSupport.toMillis(sow.getCreateTime()),
                sow != null ? sow.getContent() : "待播种"));
        FarmRecordDO lastRecord = records.isEmpty() ? null : records.get(records.size() - 1);
        stages.add(stage("grow", "生长", lastRecord == null || lastRecord.getCreateTime() == null ? null : FarmAppSupport.toMillis(lastRecord.getCreateTime()),
                "已记录 " + records.size() + " 次农事，每次带照片、定位和环境数据"));
        FarmInputRecordDO lastInput = inputs.isEmpty() ? null : inputs.get(inputs.size() - 1);
        stages.add(stage("input", "投入品", lastInput == null || lastInput.getUsedAt() == null ? null : FarmAppSupport.toMillis(lastInput.getUsedAt()),
                "农场投入 " + farmInputs + " 次，认养人自购 " + memberInputs + " 次"
                        + (untraced > 0 ? "；" + untraced + " 笔自购农资待登记，档案标「待补充」" : "")));
        FarmHarvestDO lastHarvest = harvests.isEmpty() ? null : harvests.get(harvests.size() - 1);
        LocalDate hd = lastHarvest == null ? null : lastHarvest.getHarvestDate();
        stages.add(stage("harvest", "采收", hd == null ? null : hd.atStartOfDay(ZONE).toInstant().toEpochMilli(),
                !harvests.isEmpty() ? "已采收 " + harvests.size() + " 批，共 " + jin(ad.getHarvestedJin()) + " 斤" : "还没到采收期"));
        FarmShipmentDO lastShipment = shipments.isEmpty() ? null : shipments.get(shipments.size() - 1);
        stages.add(stage("ship", "配送", lastShipment == null || lastShipment.getCreateTime() == null ? null : FarmAppSupport.toMillis(lastShipment.getCreateTime()),
                !shipments.isEmpty() ? "已配送 " + shipments.size() + " 次，全程冷链" : "还没有配送"));

        FarmAppTraceRespVO v = new FarmAppTraceRespVO();
        v.setStages(stages);
        v.setInputs(BeanUtils.toBean(inputs, FarmAppTraceRespVO.Input.class));
        v.setPendingTrace(untraced > 0);
        return v;
    }

    /** mock 184 行：复用后台资产环境数据（同一套确定性生成算法） */
    public Map<String, Object> env(Long adoptionId) {
        FarmAdoptionDO ad = mine(adoptionId);
        return assetService.env(ad.getAssetId());
    }

    /** mock 189–194 行 */
    public FarmAppCameraRespVO camera(Long adoptionId) {
        FarmAdoptionDO ad = mine(adoptionId);
        FarmAssetDO a = ad.getAssetId() == null ? null : lookup.assets(List.of(ad.getAssetId())).get(ad.getAssetId());
        if (a == null) throw exception(NOT_FOUND, "资产");
        boolean online = a.getCameraName() != null && !a.getCameraName().isEmpty();
        FarmAppCameraRespVO v = new FarmAppCameraRespVO();
        v.setName(online ? a.getCameraName() : "暂无摄像头");
        v.setOnline(online);
        v.setPoster(FarmAppSupport.placeholderImage(a.getCode() + " 实时画面", "#37474f"));
        v.setTip("演示环境为视频占位，正式版接入萤石 / 海康直播流");
        return v;
    }

    /** mock 272–277 行：收获配送，adoptionId 可选；传了但不是自己的认养 → 404 */
    public List<FarmShipmentRespVO> shipments(Long adoptionId) {
        Long memberId = support.memberId();
        if (adoptionId != null) mine(adoptionId);
        LambdaQueryWrapper<FarmShipmentDO> w = new LambdaQueryWrapper<FarmShipmentDO>()
                .eq(FarmShipmentDO::getMemberId, memberId);
        if (adoptionId != null) w.eq(FarmShipmentDO::getAdoptionId, adoptionId);
        w.orderByDesc(FarmShipmentDO::getCreateTime).orderByDesc(FarmShipmentDO::getId);
        return shipmentService.toViews(shipmentMapper.selectList(w));
    }

    // ---------- helpers ----------

    /** 取自己的认养，不是自己的或不存在 → 404「认养不存在」 */
    private FarmAdoptionDO mine(Long id) {
        FarmAdoptionDO ad = support.myAdoption(id);
        if (ad == null) throw exception(NOT_FOUND, "认养");
        return ad;
    }

    /** 对应 mock adoptionView：商品名、封面、资产编号、基地名、托管方式名、自管未完成任务数 */
    private List<FarmOrderAdoptionVO> views(List<FarmAdoptionDO> list) {
        List<FarmOrderAdoptionVO> out = new ArrayList<>();
        if (list.isEmpty()) return out;
        Map<Long, FarmProductDO> products = lookup.products(list.stream().map(FarmAdoptionDO::getProductId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmAdoptionDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmBaseDO> bases = lookup.bases(assets.values().stream().map(FarmAssetDO::getBaseId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmAdoptionDO::getMemberId).collect(Collectors.toList()));

        Map<Long, Long> todo = new HashMap<>();
        List<FarmTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<FarmTaskDO>()
                .in(FarmTaskDO::getAdoptionId, list.stream().map(FarmAdoptionDO::getId).collect(Collectors.toList()))
                .eq(FarmTaskDO::getOwner, "member")
                .eq(FarmTaskDO::getStatus, 0));
        for (FarmTaskDO t : tasks) todo.merge(t.getAdoptionId(), 1L, Long::sum);

        for (FarmAdoptionDO ad : list) {
            FarmOrderAdoptionVO v = BeanUtils.toBean(ad, FarmOrderAdoptionVO.class);
            FarmProductDO p = ad.getProductId() == null ? null : products.get(ad.getProductId());
            FarmAssetDO a = ad.getAssetId() == null ? null : assets.get(ad.getAssetId());
            FarmBaseDO b = a == null || a.getBaseId() == null ? null : bases.get(a.getBaseId());
            FarmMemberDO m = ad.getMemberId() == null ? null : members.get(ad.getMemberId());
            v.setProductTitle(p == null || p.getTitle() == null ? "" : p.getTitle());
            v.setCover(p == null || p.getCover() == null ? "" : p.getCover());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            v.setAssetType(a == null || a.getAssetType() == null ? "" : a.getAssetType());
            v.setBaseName(b == null || b.getName() == null ? "" : b.getName());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setCareModeName(FarmViewLookup.careModeName(ad.getCareMode()));
            v.setSelfTodoCount(todo.getOrDefault(ad.getId(), 0L));
            out.add(v);
        }
        return out;
    }

    private static Map<String, Object> stage(String key, String title, Long time, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("title", title);
        m.put("time", time);
        m.put("content", content);
        return m;
    }

    /** 斤数显示：去掉多余的 0（12.50 → 12.5，0.0 → 0），与 JS 数字拼接一致 */
    private static String jin(BigDecimal v) {
        if (v == null || v.signum() == 0) return "0";
        return v.stripTrailingZeros().toPlainString();
    }

    /** 手机号脱敏：138****1234（mock maskMobile） */
    private static String maskMobile(String mobile) {
        if (mobile == null) return "";
        return mobile.replaceFirst("^(\\d{3})\\d{4}(\\d{4})$", "$1****$2");
    }
}
