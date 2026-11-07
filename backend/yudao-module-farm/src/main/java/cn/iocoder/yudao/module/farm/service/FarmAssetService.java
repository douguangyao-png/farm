package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetSaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 资产（契约 2.3；mock 见 farm-admin.ts 140-176、common.ts envAt/envView） */
@Service
public class FarmAssetService {

    private static final Set<String> ASSET_TYPES = Set.of("plot", "animal", "tree");

    @Resource
    private FarmAssetMapper assetMapper;
    @Resource
    private FarmBaseMapper baseMapper;
    @Resource
    private FarmAdoptionMapper adoptionMapper;
    @Resource
    private FarmMemberMapper memberMapper;

    public PageResult<FarmAssetRespVO> page(FarmAssetPageReqVO req) {
        PageResult<FarmAssetDO> p = assetMapper.selectPage(req, new LambdaQueryWrapperX<FarmAssetDO>()
                .eqIfPresent(FarmAssetDO::getBaseId, req.getBaseId())
                .eqIfPresent(FarmAssetDO::getAssetType, req.getAssetType())
                .eqIfPresent(FarmAssetDO::getStatus, req.getStatus())
                .likeIfPresent(FarmAssetDO::getCode, req.getCode())
                .orderByAsc(FarmAssetDO::getId));
        return new PageResult<>(toViews(p.getList()), p.getTotal());
    }

    public FarmAssetDO get(Long id) {
        FarmAssetDO a = id == null ? null : assetMapper.selectById(id);
        if (a == null) throw exception(NOT_FOUND, "资产");
        return a;
    }

    public FarmAssetRespVO getView(Long id) {
        return toViews(List.of(get(id))).get(0);
    }

    public Long create(FarmAssetSaveReqVO req) {
        FarmBaseDO base = req.getBaseId() == null ? null : baseMapper.selectById(req.getBaseId());
        if (base == null) throw exception(BAD_REQUEST, "请选择基地");
        if (req.getAssetType() == null || !ASSET_TYPES.contains(req.getAssetType())) throw exception(BAD_REQUEST, "请选择资产类型");
        if (StrUtil.isBlank(req.getCode())) throw exception(BAD_REQUEST, "编号不能为空");
        String code = req.getCode().trim();
        if (assetMapper.selectCount(new LambdaQueryWrapperX<FarmAssetDO>()
                .eq(FarmAssetDO::getBaseId, base.getId()).eq(FarmAssetDO::getCode, code)) > 0) {
            throw exception(BAD_REQUEST, "编号已存在");
        }
        FarmAssetDO a = new FarmAssetDO();
        a.setBaseId(base.getId());
        a.setAssetType(req.getAssetType());
        a.setCode(code);
        a.setName(StrUtil.isNotBlank(req.getName()) ? req.getName().trim() : code);
        a.setSpecs(req.getSpecs() == null ? new LinkedHashMap<>() : req.getSpecs());
        a.setStatus(0);
        a.setCameraName(StrUtil.isEmpty(req.getCameraName()) ? null : req.getCameraName());
        a.setSensorName(StrUtil.isEmpty(req.getSensorName()) ? null : req.getSensorName());
        assetMapper.insert(a);
        return a.getId();
    }

    public void update(FarmAssetSaveReqVO req) {
        FarmAssetDO a = get(req.getId());
        if (req.getCode() != null && assetMapper.selectCount(new LambdaQueryWrapperX<FarmAssetDO>()
                .eq(FarmAssetDO::getBaseId, a.getBaseId()).eq(FarmAssetDO::getCode, req.getCode())
                .ne(FarmAssetDO::getId, a.getId())) > 0) {
            throw exception(BAD_REQUEST, "编号已存在");
        }
        if (req.getCode() != null) a.setCode(req.getCode());
        if (req.getName() != null) a.setName(req.getName());
        if (req.getSpecs() != null) a.setSpecs(req.getSpecs());
        if (req.getCameraName() != null) a.setCameraName(req.getCameraName());
        if (req.getSensorName() != null) a.setSensorName(req.getSensorName());
        if (req.getStatus() != null) {
            if (a.getStatus() != null && a.getStatus() == 10 && req.getStatus() != 10) {
                throw exception(BAD_REQUEST, "已认养的资产不能停用");
            }
            a.setStatus(req.getStatus());
        }
        assetMapper.updateById(a);
    }

    // ---------- view ----------

    private List<FarmAssetRespVO> toViews(List<FarmAssetDO> list) {
        List<FarmAssetRespVO> out = new ArrayList<>();
        if (CollUtil.isEmpty(list)) return out;
        Set<Long> baseIds = list.stream().map(FarmAssetDO::getBaseId).collect(Collectors.toSet());
        Map<Long, String> baseNames = new HashMap<>();
        for (FarmBaseDO b : baseMapper.selectList(FarmBaseDO::getId, baseIds)) baseNames.put(b.getId(), b.getName());

        Set<Long> assetIds = list.stream().map(FarmAssetDO::getId).collect(Collectors.toSet());
        // 每个资产取第一条 status=10 的认养记录（与 mock 的 find 一致：按 id 升序取第一条）
        List<FarmAdoptionDO> ads = adoptionMapper.selectList(new LambdaQueryWrapperX<FarmAdoptionDO>()
                .in(FarmAdoptionDO::getAssetId, assetIds).eq(FarmAdoptionDO::getStatus, 10).orderByAsc(FarmAdoptionDO::getId));
        Map<Long, FarmAdoptionDO> adByAsset = new HashMap<>();
        for (FarmAdoptionDO ad : ads) adByAsset.putIfAbsent(ad.getAssetId(), ad);
        Set<Long> memberIds = ads.stream().map(FarmAdoptionDO::getMemberId).collect(Collectors.toSet());
        Map<Long, String> memberNames = new HashMap<>();
        for (FarmMemberDO m : memberMapper.selectList(FarmMemberDO::getId, memberIds)) memberNames.put(m.getId(), m.getNickname());

        for (FarmAssetDO a : list) {
            FarmAssetRespVO v = BeanUtils.toBean(a, FarmAssetRespVO.class);
            v.setBaseName(baseNames.getOrDefault(a.getBaseId(), ""));
            FarmAdoptionDO ad = adByAsset.get(a.getId());
            if (ad != null) {
                v.setAdoptionId(ad.getId());
                v.setMemberName(memberNames.getOrDefault(ad.getMemberId(), ""));
                v.setCareMode(ad.getCareMode());
            } else {
                v.setMemberName("");
            }
            out.add(v);
        }
        return out;
    }

    // ---------- 环境数据（IoT 演示）：按资产 id 和小时确定性生成，刷新不乱跳（对应 common.ts） ----------

    private static double wave(long seed, long hour, double base, double amp) {
        final int period = 24;
        long phase = (seed * 37) % period;
        double noise = Math.sin(seed * 12.9898 + hour * 78.233) * 0.25;
        return base + amp * Math.sin(((double) (hour + phase) / period) * 2 * Math.PI) + noise;
    }

    private static double round1(double n) {
        return Math.round(n * 10) / 10.0;
    }

    private static Map<String, Object> envAt(FarmAssetDO asset, long ts) {
        long hour = Math.floorDiv(ts, 3600_000L);
        long id = asset.getId();
        boolean dryPlot = "A-03".equals(asset.getCode()); // A-03 演示「土壤湿度低于 22% 触发浇水任务」
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("airTemp", round1(wave(id, hour, 18, 5)));
        m.put("airHumidity", Math.round(wave(id + 3, hour, 68, 12)));
        m.put("soilMoisture", round1(dryPlot ? wave(id, hour, 19.5, 1.2) : wave(id + 7, hour, 30, 3)));
        m.put("soilTemp", round1(wave(id + 1, hour, 15, 2.5)));
        m.put("ph", round1(6.6 + Math.sin(id) * 0.3));
        m.put("light", Math.max(0L, Math.round(wave(id, hour, 18000, 22000))));
        return m;
    }

    public Map<String, Object> env(Long assetId) {
        FarmAssetDO asset = get(assetId);
        long now = System.currentTimeMillis();
        Map<String, Object> current = envAt(asset, now);
        current.put("updateTime", now);
        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            long ts = Math.floorDiv(now, 3600_000L) * 3600_000L - (23 - i) * 3600_000L;
            Map<String, Object> e = envAt(asset, ts);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("ts", ts);
            point.put("airTemp", e.get("airTemp"));
            point.put("airHumidity", e.get("airHumidity"));
            point.put("soilMoisture", e.get("soilMoisture"));
            series.add(point);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", current);
        out.put("series", series);
        out.put("sensorName", asset.getSensorName());
        out.put("demo", true);
        return out;
    }
}
