package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmAdoptionSimpleRespVO;
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
import jakarta.annotation.Resource;
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
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;

/** 接口契约 2.9 收成（mock：farm-admin.ts 355–386 行） */
@Service
public class FarmHarvestService {

    /** 采收日期按北京时间换算（与前端传来的「当天 0 点」毫秒时间戳对应，不受服务器时区影响） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private FarmHarvestMapper harvestMapper;
    @Resource
    private FarmAdoptionMapper adoptionMapper;
    @Resource
    private FarmAssetMapper assetMapper;
    @Resource
    private FarmRecordMapper recordMapper;
    @Resource
    private FarmViewLookup lookup;

    public PageResult<FarmHarvestRespVO> page(FarmHarvestPageReqVO req) {
        LambdaQueryWrapperX<FarmHarvestDO> w = new LambdaQueryWrapperX<FarmHarvestDO>();
        if (StrUtil.isNotEmpty(req.getAssetCode())) {
            List<Long> assetIds = assetMapper.selectList(new LambdaQueryWrapperX<FarmAssetDO>()
                            .like(FarmAssetDO::getCode, req.getAssetCode()))
                    .stream().map(FarmAssetDO::getId).collect(Collectors.toList());
            if (assetIds.isEmpty()) return PageResult.empty();
            w.in(FarmHarvestDO::getAssetId, assetIds);
        }
        if (req.getShipped() != null) {
            if (req.getShipped()) w.isNotNull(FarmHarvestDO::getShipmentId);
            else w.isNull(FarmHarvestDO::getShipmentId);
        }
        w.orderByDesc(FarmHarvestDO::getHarvestDate).orderByDesc(FarmHarvestDO::getId);
        PageResult<FarmHarvestDO> p = harvestMapper.selectPage(req, w);

        List<FarmHarvestDO> list = p.getList();
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
        return new PageResult<>(out, p.getTotal());
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
        if (req.getWeightJin() == null || req.getWeightJin().compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(BAD_REQUEST, "重量要大于 0");
        }
        if (StrUtil.isBlank(req.getCrop())) throw exception(BAD_REQUEST, "请填写作物");
        BigDecimal weightJin = req.getWeightJin().setScale(1, RoundingMode.HALF_UP);
        String crop = req.getCrop().trim();
        String remark = req.getRemark() == null ? "" : req.getRemark();
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
        rec.setContent((StrUtil.isEmpty(nickname) ? "农场" : nickname) + "录入收成，称重 " + weightText + " 斤。" + remark);
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
