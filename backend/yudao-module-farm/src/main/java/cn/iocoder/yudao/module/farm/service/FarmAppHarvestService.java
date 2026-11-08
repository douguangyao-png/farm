package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.harvest.vo.FarmHarvestRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmHarvestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** C 端收成详情（含环境快照）：只能看自己认养下的收成（mock：routes/farm-p1b.ts 的 /harvest/list、/harvest/get） */
@Service
public class FarmAppHarvestService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private FarmHarvestMapper harvestMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmAppSupport support;
    @Autowired
    private FarmViewLookup lookup;

    public List<FarmHarvestRespVO> list(Long adoptionId) {
        Long memberId = support.memberId();
        Set<Long> mine = adoptionMapper.selectList(new LambdaQueryWrapperX<FarmAdoptionDO>().eq(FarmAdoptionDO::getMemberId, memberId))
                .stream().map(FarmAdoptionDO::getId).collect(Collectors.toSet());
        if (mine.isEmpty() || (adoptionId != null && !mine.contains(adoptionId))) return new ArrayList<>();
        List<FarmHarvestDO> rows = harvestMapper.selectList(new LambdaQueryWrapperX<FarmHarvestDO>()
                .in(FarmHarvestDO::getAdoptionId, adoptionId != null ? List.of(adoptionId) : mine)
                .orderByDesc(FarmHarvestDO::getHarvestDate).orderByDesc(FarmHarvestDO::getId));
        return toViews(rows);
    }

    public FarmHarvestRespVO get(Long id) {
        FarmHarvestDO h = id == null ? null : harvestMapper.selectById(id);
        if (h == null || support.myAdoption(h.getAdoptionId()) == null) throw exception(NOT_FOUND, "收成");
        return toViews(List.of(h)).get(0);
    }

    private List<FarmHarvestRespVO> toViews(List<FarmHarvestDO> rows) {
        Map<Long, FarmAssetDO> assets = lookup.assets(rows.stream().map(FarmHarvestDO::getAssetId).collect(Collectors.toList()));
        List<FarmHarvestRespVO> out = new ArrayList<>();
        for (FarmHarvestDO h : rows) {
            FarmAssetDO a = h.getAssetId() == null ? null : assets.get(h.getAssetId());
            FarmHarvestRespVO v = new FarmHarvestRespVO();
            v.setId(h.getId());
            v.setAdoptionId(h.getAdoptionId());
            v.setAssetId(h.getAssetId());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            v.setMemberName("");
            v.setCrop(h.getCrop());
            v.setWeightJin(h.getWeightJin());
            v.setHarvestDate(h.getHarvestDate() == null ? null : h.getHarvestDate().atStartOfDay(ZONE).toInstant().toEpochMilli());
            v.setPhotos(h.getPhotos() == null ? new ArrayList<>() : h.getPhotos());
            v.setRemark(h.getRemark());
            v.setShipmentId(h.getShipmentId());
            v.setEnvSnapshot(FarmHarvestService.envOf(h, a));
            out.add(v);
        }
        return out;
    }
}
