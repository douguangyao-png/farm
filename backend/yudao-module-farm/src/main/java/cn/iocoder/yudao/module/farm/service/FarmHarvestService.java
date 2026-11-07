package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmAdoptionSimpleRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmHarvestMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;

/** 接口契约 2.9 收成（mock：farm-admin.ts /harvest、farm/stats.ts） */
@Service
public class FarmHarvestService {

    /** 采收日期按北京时间换算（与前端传来的「当天 0 点」毫秒时间戳对应，不受服务器时区影响） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private FarmHarvestMapper harvestMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmViewLookup lookup;

    /** 列表 + 统计卡（stats 字段见契约 2.9；mock：farm/stats.ts harvestPage） */
    public FarmFulfillPage<FarmHarvestRespVO> page(FarmHarvestPageReqVO req) {
        List<FarmHarvestDO> allRows = harvestMapper.selectList(new LambdaQueryWrapperX<FarmHarvestDO>()
                .orderByDesc(FarmHarvestDO::getHarvestDate).orderByDesc(FarmHarvestDO::getId));
        LocalDate m0 = LocalDate.now(ZONE).withDayOfMonth(1);
        List<FarmHarvestDO> unshipped = allRows.stream().filter(h -> h.getShipmentId() == null).collect(Collectors.toList());
        Map<String, Object> stats = FarmFulfillPage.m(
                "totalCount", allRows.size(),
                "totalWeightJin", FarmFulfillPage.r1(sumWeight(allRows)),
                "monthWeightJin", FarmFulfillPage.r1(sumWeight(allRows.stream()
                        .filter(h -> h.getHarvestDate() != null && !h.getHarvestDate().isBefore(m0)).collect(Collectors.toList()))),
                "unshippedCount", unshipped.size(),
                "unshippedWeightJin", FarmFulfillPage.r1(sumWeight(unshipped)));

        List<FarmHarvestDO> filtered = allRows;
        if (StrUtil.isNotEmpty(req.getAssetCode())) {
            Set<Long> assetIds = assetMapper.selectList(new LambdaQueryWrapperX<FarmAssetDO>()
                            .like(FarmAssetDO::getCode, req.getAssetCode()))
                    .stream().map(FarmAssetDO::getId).collect(Collectors.toSet());
            filtered = filtered.stream().filter(h -> assetIds.contains(h.getAssetId())).collect(Collectors.toList());
        }
        if (req.getShipped() != null) {
            boolean shipped = req.getShipped();
            filtered = filtered.stream().filter(h -> (h.getShipmentId() != null) == shipped).collect(Collectors.toList());
        }
        List<FarmHarvestDO> list = FarmFulfillPage.slice(filtered, req);
        Map<Long, FarmAdoptionDO> adoptions = lookup.adoptions(list.stream().map(FarmHarvestDO::getAdoptionId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmHarvestDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(adoptions.values().stream().map(FarmAdoptionDO::getMemberId).collect(Collectors.toList()));
        List<FarmHarvestRespVO> out = new ArrayList<>();
        for (FarmHarvestDO h : list) {
            FarmHarvestRespVO v = new FarmHarvestRespVO();
            v.setId(h.getId());
            v.setAdoptionId(h.getAdoptionId());
            v.setAssetId(h.getAssetId());
            FarmAssetDO a = h.getAssetId() == null ? null : assets.get(h.getAssetId());
            v.setAssetCode(a == null ? "" : a.getCode());
            FarmAdoptionDO ad = h.getAdoptionId() == null ? null : adoptions.get(h.getAdoptionId());
            FarmMemberDO m = ad == null || ad.getMemberId() == null ? null : members.get(ad.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setCrop(h.getCrop());
            v.setWeightJin(h.getWeightJin());
            v.setHarvestDate(h.getHarvestDate() == null ? null : h.getHarvestDate().atStartOfDay(ZONE).toInstant().toEpochMilli());
            v.setPhotos(h.getPhotos() == null ? new ArrayList<>() : h.getPhotos());
            v.setRemark(h.getRemark());
            v.setShipmentId(h.getShipmentId());
            out.add(v);
        }
        FarmFulfillPage<FarmHarvestRespVO> result = new FarmFulfillPage<>();
        result.setList(out);
        result.setTotal((long) filtered.size());
        result.setStats(stats);
        return result;
    }

    private static double sumWeight(List<FarmHarvestDO> list) {
        return list.stream().map(FarmHarvestDO::getWeightJin).filter(w -> w != null)
                .mapToDouble(BigDecimal::doubleValue).sum();
    }

    /** 认养人下拉：本租户状态为 10（认养中）的认养关系 */
    public List<FarmAdoptionSimpleRespVO> adoptionSimpleList() {
        List<FarmAdoptionDO> list = adoptionMapper.selectList(new LambdaQueryWrapperX<FarmAdoptionDO>()
                .eq(FarmAdoptionDO::getStatus, 10).orderByAsc(FarmAdoptionDO::getId));
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmAdoptionDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmAdoptionDO::getMemberId).collect(Collectors.toList()));
        List<FarmAdoptionSimpleRespVO> out = new ArrayList<>();
        for (FarmAdoptionDO ad : list) {
            FarmAssetDO a = ad.getAssetId() == null ? null : assets.get(ad.getAssetId());
            FarmMemberDO m = ad.getMemberId() == null ? null : members.get(ad.getMemberId());
            String assetCode = a == null ? "" : a.getCode();
            String memberName = m == null || m.getNickname() == null ? "" : m.getNickname();
            FarmAdoptionSimpleRespVO v = new FarmAdoptionSimpleRespVO();
            v.setId(ad.getId());
            v.setLabel(assetCode + " · " + memberName + " · " + FarmViewLookup.careModeName(ad.getCareMode()));
            v.setAssetCode(assetCode);
            v.setMemberName(memberName);
            v.setCareMode(ad.getCareMode());
            out.add(v);
        }
        return out;
    }

    /** 录入收成：累加认养的 harvestedJin，并写一条已审核的生长档案（kind=harvest） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(FarmHarvestCreateReqVO req) {
        FarmAdoptionDO ad = req.getAdoptionId() == null ? null : adoptionMapper.selectById(req.getAdoptionId());
        if (ad == null) throw exception(BAD_REQUEST, "请选择认养关系");
        // 支持多品类明细 items；没传 items 时按老的单作物 crop + weightJin
        List<FarmHarvestCreateReqVO.Item> rawItems = req.getItems();
        if (rawItems == null || rawItems.isEmpty()) {
            FarmHarvestCreateReqVO.Item one = new FarmHarvestCreateReqVO.Item();
            one.setCrop(req.getCrop());
            one.setWeightJin(req.getWeightJin());
            rawItems = List.of(one);
        }
        List<String> cropNames = new ArrayList<>();
        List<String> details = new ArrayList<>();
        BigDecimal weightJin = BigDecimal.ZERO;
        for (FarmHarvestCreateReqVO.Item it : rawItems) {
            if (StrUtil.isBlank(it.getCrop())) throw exception(BAD_REQUEST, "请填写作物");
            if (it.getWeightJin() == null || it.getWeightJin().compareTo(BigDecimal.ZERO) <= 0) {
                throw exception(BAD_REQUEST, "重量要大于 0");
            }
            BigDecimal w = it.getWeightJin().setScale(1, RoundingMode.HALF_UP);
            weightJin = weightJin.add(w);
            cropNames.add(it.getCrop().trim());
            details.add(it.getCrop().trim() + " " + w.stripTrailingZeros().toPlainString() + " 斤");
        }
        weightJin = weightJin.setScale(1, RoundingMode.HALF_UP);
        String crop = String.join("、", cropNames);
        String farmType = StrUtil.isBlank(req.getFarmType()) ? "采摘采收" : req.getFarmType().trim();
        String userRemark = req.getRemark() == null ? "" : req.getRemark().trim();
        String remark = userRemark;
        if (rawItems.size() > 1) {
            String detail = "明细：" + String.join("、", details);
            remark = userRemark.isEmpty() ? detail : userRemark + "；" + detail;
        }
        // 库里 crop 是 varchar(64)、remark 是 varchar(255)，超了给明确提示，不让数据库报错
        if (crop.length() > 64) throw exception(BAD_REQUEST, "品类名称合计不能超过 64 个字");
        if (remark.length() > 255) throw exception(BAD_REQUEST, "备注加明细不能超过 255 个字");
        List<String> photos = req.getPhotos() == null ? new ArrayList<>() : req.getPhotos();
        long hd = req.getHarvestDate() == null || req.getHarvestDate() == 0L ? System.currentTimeMillis() : req.getHarvestDate();
        LocalDate harvestDate = Instant.ofEpochMilli(hd).atZone(ZONE).toLocalDate();

        FarmHarvestDO h = new FarmHarvestDO();
        h.setAdoptionId(ad.getId());
        h.setAssetId(ad.getAssetId());
        h.setCrop(crop);
        h.setWeightJin(weightJin);
        h.setHarvestDate(harvestDate);
        h.setPhotos(photos);
        h.setRemark(remark);
        harvestMapper.insert(h);

        // 累加认养的已收成斤数（SQL 内自增，避免并发覆盖）
        adoptionMapper.update(null, new LambdaUpdateWrapper<FarmAdoptionDO>()
                .eq(FarmAdoptionDO::getId, ad.getId())
                .setSql("harvested_jin = harvested_jin + " + weightJin.toPlainString()));

        // 采收写进生长档案（经营者录入的，直接算已审核）
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        String weightText = weightJin.stripTrailingZeros().toPlainString();
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("airTemp", 0);
        env.put("airHumidity", 0);
        env.put("soilMoisture", 0);
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("exifTime", true);
        checks.put("gpsFence", true);
        checks.put("duplicate", true);
        FarmRecordDO rec = new FarmRecordDO();
        rec.setAdoptionId(ad.getId());
        rec.setAssetId(ad.getAssetId());
        rec.setStewardId(ad.getStewardId());
        rec.setTaskId(null);
        rec.setKind("harvest");
        rec.setTitle("采收 " + crop + " " + weightText + " 斤");
        rec.setContent((StrUtil.isEmpty(nickname) ? "农场" : nickname) + "录入收成（农事类型：" + farmType + "），称重 " + weightText + " 斤。" + remark);
        rec.setPhotos(photos);
        rec.setLng(BigDecimal.ZERO);
        rec.setLat(BigDecimal.ZERO);
        rec.setEnv(env);
        rec.setChecks(checks);
        rec.setStatus(10);
        rec.setAuditRemark(null);
        rec.setAuditTime(LocalDateTime.now());
        recordMapper.insert(rec);
        return h.getId();
    }
}
