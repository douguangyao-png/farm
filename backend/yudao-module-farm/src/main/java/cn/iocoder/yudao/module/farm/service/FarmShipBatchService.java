package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.shipbatch.vo.FarmShipBatchCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipbatch.vo.FarmShipBatchPageReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmShipBatchDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmShipmentDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmHarvestMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmShipBatchMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmShipmentMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 配送批次（第一期补齐；mock：routes/farm-p1b.ts 的 /ship-batch/**）。状态 0 待发 -> 10 已发出 -> 20 已完成 */
@Service
public class FarmShipBatchService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private FarmShipBatchMapper batchMapper;
    @Autowired
    private FarmShipmentMapper shipmentMapper;
    @Autowired
    private FarmHarvestMapper harvestMapper;
    @Autowired
    private FarmShipmentService shipmentService;
    @Autowired
    private FarmViewLookup lookup;
    @Autowired
    private FarmNoGenerator noGenerator;

    public PageResult<Map<String, Object>> page(FarmShipBatchPageReqVO req) {
        PageResult<FarmShipBatchDO> p = batchMapper.selectPage(req, new LambdaQueryWrapperX<FarmShipBatchDO>()
                .eqIfPresent(FarmShipBatchDO::getStatus, req.getStatus())
                .likeIfPresent(FarmShipBatchDO::getNo, req.getNo())
                .orderByDesc(FarmShipBatchDO::getCreateTime).orderByDesc(FarmShipBatchDO::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (FarmShipBatchDO b : p.getList()) list.add(view(b));
        return new PageResult<>(list, p.getTotal());
    }

    public Map<String, Object> get(Long id) {
        FarmShipBatchDO b = id == null ? null : batchMapper.selectById(id);
        if (b == null) throw exception(NOT_FOUND, "配送批次");
        Map<String, Object> v = view(b);
        v.put("shipments", shipmentService.toViews(shipmentsOf(b.getId())));
        return v;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(FarmShipBatchCreateReqVO req) {
        FarmHarvestDO h = req.getHarvestId() == null ? null : harvestMapper.selectById(req.getHarvestId());
        if (h == null) throw exception(BAD_REQUEST, "请选择收成");
        if (req.getPlanDate() == null || req.getPlanDate() <= 0) throw exception(BAD_REQUEST, "请选择计划发货日期");
        String remark = req.getRemark() == null ? "" : req.getRemark().trim();
        if (remark.length() > 255) throw exception(BAD_REQUEST, "备注不能超过 255 个字");
        List<Long> ids = req.getShipmentIds() == null ? new ArrayList<>() : req.getShipmentIds();
        if (ids.isEmpty()) throw exception(BAD_REQUEST, "请选择配送单");
        if (new HashSet<>(ids).size() != ids.size()) throw exception(BAD_REQUEST, "配送单重复");
        List<FarmShipmentDO> ships = shipmentMapper.selectList(FarmShipmentDO::getId, ids);
        if (ships.size() != ids.size()) throw exception(BAD_REQUEST, "配送单不存在");
        if (ships.stream().anyMatch(s -> s.getBatchId() != null)) throw exception(BAD_REQUEST, "有配送单已在其它批次里");

        FarmShipBatchDO b = new FarmShipBatchDO();
        b.setNo(noGenerator.next("PC"));
        b.setHarvestId(h.getId());
        b.setPlanDate(Instant.ofEpochMilli(req.getPlanDate()).atZone(ZONE).toLocalDate());
        b.setStatus(0);
        b.setRemark(remark);
        batchMapper.insert(b);
        // 条件更新：只有 batch_id 仍为空才写入，并发入批失败则整个事务回滚
        int rows = shipmentMapper.update(null, new LambdaUpdateWrapper<FarmShipmentDO>()
                .in(FarmShipmentDO::getId, ids).isNull(FarmShipmentDO::getBatchId).set(FarmShipmentDO::getBatchId, b.getId()));
        if (rows != ids.size()) throw exception(BAD_REQUEST, "有配送单已在其它批次里");
        return b.getId();
    }

    /** 发货：只允许 status=0；批次下待发（0）的配送单置已发出（10）并追加一条轨迹 */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id) {
        FarmShipBatchDO b = id == null ? null : batchMapper.selectById(id);
        if (b == null) throw exception(NOT_FOUND, "配送批次");
        if (b.getStatus() == null || b.getStatus() != 0) throw exception(BAD_REQUEST, "只有待发的批次能发货");
        int rows = batchMapper.update(null, new LambdaUpdateWrapper<FarmShipBatchDO>()
                .eq(FarmShipBatchDO::getId, id).eq(FarmShipBatchDO::getStatus, 0).set(FarmShipBatchDO::getStatus, 10));
        if (rows == 0) throw exception(BAD_REQUEST, "只有待发的批次能发货");
        long now = System.currentTimeMillis();
        for (FarmShipmentDO s : shipmentsOf(id)) {
            if (s.getStatus() == null || s.getStatus() != 0) continue;
            List<Map<String, Object>> tracks = new ArrayList<>();
            if (s.getTracks() != null) tracks.addAll(s.getTracks());
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("time", now);
            t.put("content", "批次 " + b.getNo() + " 已发出");
            tracks.add(t);
            s.setStatus(10);
            s.setTracks(tracks);
            shipmentMapper.updateById(s);
        }
    }

    /** 完成：只允许 status=10 */
    public void finish(Long id) {
        FarmShipBatchDO b = id == null ? null : batchMapper.selectById(id);
        if (b == null) throw exception(NOT_FOUND, "配送批次");
        int rows = batchMapper.update(null, new LambdaUpdateWrapper<FarmShipBatchDO>()
                .eq(FarmShipBatchDO::getId, id).eq(FarmShipBatchDO::getStatus, 10).set(FarmShipBatchDO::getStatus, 20));
        if (rows == 0) throw exception(BAD_REQUEST, "只有已发出的批次能完成");
    }

    private List<FarmShipmentDO> shipmentsOf(Long batchId) {
        return shipmentMapper.selectList(new LambdaQueryWrapperX<FarmShipmentDO>()
                .eq(FarmShipmentDO::getBatchId, batchId).orderByAsc(FarmShipmentDO::getId));
    }

    private Map<String, Object> view(FarmShipBatchDO b) {
        List<FarmShipmentDO> ships = shipmentsOf(b.getId());
        FarmHarvestDO h = b.getHarvestId() == null ? null : harvestMapper.selectById(b.getHarvestId());
        FarmAssetDO asset = h == null || h.getAssetId() == null ? null : lookup.assets(List.of(h.getAssetId())).get(h.getAssetId());
        double w = ships.stream().map(FarmShipmentDO::getWeightJin).filter(x -> x != null).mapToDouble(BigDecimal::doubleValue).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("no", b.getNo());
        m.put("harvestId", b.getHarvestId());
        m.put("planDate", b.getPlanDate() == null ? null : b.getPlanDate().atStartOfDay(ZONE).toInstant().toEpochMilli());
        m.put("status", b.getStatus());
        m.put("remark", b.getRemark() == null ? "" : b.getRemark());
        m.put("createTime", FarmAppSupport.toMillis(b.getCreateTime()));
        m.put("harvestCrop", h == null || h.getCrop() == null ? "" : h.getCrop());
        m.put("assetCode", asset == null || asset.getCode() == null ? "" : asset.getCode());
        m.put("shipmentCount", ships.size());
        m.put("weightJin", Math.round(w * 10d) / 10d);
        return m;
    }
}
