package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.shipment.vo.FarmShipmentRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmHarvestDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmShipmentDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmHarvestMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmShipmentMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 接口契约 2.9 配送（mock：farm-admin.ts /shipment、farm/stats.ts） */
@Service
public class FarmShipmentService {

    /** 契约 1_030_000_002：订单/单据状态已变化（提示语由调用处传入，与 mock 一致） */
    private static final ErrorCode SHIPMENT_STATE = new ErrorCode(1_030_000_002, "{}");

    @Autowired
    private FarmShipmentMapper shipmentMapper;
    @Autowired
    private FarmHarvestMapper harvestMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmBaseMapper baseMapper;
    @Autowired
    private FarmNoGenerator noGenerator;
    @Autowired
    private FarmViewLookup lookup;

    /** 列表 + 统计卡 + 页签数量（stats 字段见契约 2.9；mock：farm/stats.ts shipmentPage） */
    public FarmFulfillPage<FarmShipmentRespVO> page(FarmShipmentPageReqVO req) {
        List<FarmShipmentDO> raw = shipmentMapper.selectList(new LambdaQueryWrapperX<FarmShipmentDO>()
                .orderByDesc(FarmShipmentDO::getCreateTime)
                .orderByDesc(FarmShipmentDO::getId));
        Map<String, Object> counts = FarmFulfillPage.m(
                "all", raw.size(),
                "pending", countStatus(raw, 0),
                "shipped", countStatus(raw, 10),
                "done", countStatus(raw, 20));
        Set<String> batches = new HashSet<>();
        double shippedWeight = 0;
        long coldChainCount = 0;
        for (FarmShipmentDO s : raw) {
            batches.add(batchNo(s));
            if (s.getStatus() != null && s.getStatus() == 10 && s.getWeightJin() != null) shippedWeight += s.getWeightJin().doubleValue();
            if (Boolean.TRUE.equals(s.getColdChain())) coldChainCount++;
        }
        Map<String, Object> stats = FarmFulfillPage.m(
                "counts", counts,
                "batchCount", batches.size(),
                "shippedWeightJin", FarmFulfillPage.r1(shippedWeight),
                "coldChainCount", coldChainCount);

        List<FarmShipmentDO> rows = raw.stream()
                .filter(s -> req.getStatus() == null || req.getStatus().equals(s.getStatus()))
                .filter(s -> StrUtil.isEmpty(req.getNo()) || (s.getNo() != null && s.getNo().contains(req.getNo())))
                .collect(Collectors.toList());
        FarmFulfillPage<FarmShipmentRespVO> out = new FarmFulfillPage<>();
        out.setTotal((long) rows.size());
        out.setList(toViews(FarmFulfillPage.slice(rows, req)));
        out.setStats(stats);
        return out;
    }

    private static long countStatus(List<FarmShipmentDO> list, int status) {
        return list.stream().filter(s -> s.getStatus() != null && s.getStatus() == status).count();
    }

    /** 批次号：B + 创建日期（同一天发的配送单算一个批次），例 B2026-1107 */
    private static String batchNo(FarmShipmentDO s) {
        return s.getCreateTime() == null ? "" : "B" + s.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MMdd"));
    }

    /** 创建配送单，状态直接到「运输中」，自动生成两条轨迹；同时回写收成的 shipmentId */
    @Transactional(rollbackFor = Exception.class)
    public Long create(FarmShipmentCreateReqVO req) {
        FarmHarvestDO h = req.getHarvestId() == null ? null : harvestMapper.selectById(req.getHarvestId());
        if (h == null) throw exception(BAD_REQUEST, "请选择收成");
        if (h.getShipmentId() != null) throw exception(BAD_REQUEST, "这批收成已经发过货了");
        if (StrUtil.isBlank(req.getCarrier())) throw exception(BAD_REQUEST, "请填写承运商");
        if (StrUtil.isBlank(req.getLogisticsNo())) throw exception(BAD_REQUEST, "请填写物流单号");
        FarmAdoptionDO ad = h.getAdoptionId() == null ? null : adoptionMapper.selectById(h.getAdoptionId());
        if (ad == null) throw exception(NOT_FOUND, "认养关系");
        FarmOrderDO o = ad.getOrderId() == null ? null : orderMapper.selectById(ad.getOrderId());
        FarmMemberDO m = ad.getMemberId() == null ? null : memberMapper.selectById(ad.getMemberId());
        if (m == null) throw exception(NOT_FOUND, "会员");
        FarmAssetDO asset = ad.getAssetId() == null ? null : assetMapper.selectById(ad.getAssetId());
        FarmBaseDO base = asset == null || asset.getBaseId() == null ? null : baseMapper.selectById(asset.getBaseId());

        long now = System.currentTimeMillis();
        String carrier = req.getCarrier().trim();
        List<Map<String, Object>> tracks = new ArrayList<>();
        tracks.add(track(now, (base == null ? "基地" : base.getName()) + "已打包，" + req.getCarrier() + " 揽收"));
        tracks.add(track(now + 60_000, "运输中，冷链温度 3.8℃"));

        FarmShipmentDO s = new FarmShipmentDO();
        s.setNo(noGenerator.next("PS"));
        s.setHarvestId(h.getId());
        s.setAdoptionId(ad.getId());
        s.setMemberId(m.getId());
        s.setReceiverName(firstNonEmpty(o == null ? null : o.getReceiverName(), m.getNickname()));
        s.setReceiverMobile(firstNonEmpty(o == null ? null : o.getReceiverMobile(), m.getMobile()));
        s.setAddress(firstNonEmpty(o == null ? null : o.getAddress(), m.getAddress()));
        s.setWeightJin(h.getWeightJin());
        s.setCarrier(carrier);
        s.setLogisticsNo(req.getLogisticsNo().trim());
        s.setColdChain(!Boolean.FALSE.equals(req.getColdChain()));
        s.setStatus(10);
        s.setTracks(tracks);
        shipmentMapper.insert(s);

        // 条件更新：只有 shipment_id 仍为空才写入，防止并发重复发货（失败则整个事务回滚）
        int rows = harvestMapper.update(null, new LambdaUpdateWrapper<FarmHarvestDO>()
                .eq(FarmHarvestDO::getId, h.getId())
                .isNull(FarmHarvestDO::getShipmentId)
                .set(FarmHarvestDO::getShipmentId, s.getId()));
        if (rows == 0) throw exception(BAD_REQUEST, "这批收成已经发过货了");
        return s.getId();
    }

    /** 确认签收：只有运输中（10）能签收 */
    public void sign(Long id) {
        FarmShipmentDO s = id == null ? null : shipmentMapper.selectById(id);
        if (s == null) throw exception(NOT_FOUND, "配送单");
        if (s.getStatus() == null || s.getStatus() != 10) throw exception(SHIPMENT_STATE, "只有运输中的配送单能确认签收");
        List<Map<String, Object>> tracks = new ArrayList<>();
        if (s.getTracks() != null) tracks.addAll(s.getTracks());
        tracks.add(track(System.currentTimeMillis(), "已签收，签收人：" + s.getReceiverName()));
        s.setStatus(20);
        s.setTracks(tracks);
        shipmentMapper.updateById(s);
    }

    /** 批量拼装 shipmentView（memberName / assetCode），供订单详情等复用 */
    public List<FarmShipmentRespVO> toViews(List<FarmShipmentDO> list) {
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmShipmentDO::getMemberId).collect(Collectors.toList()));
        Map<Long, FarmAdoptionDO> adoptions = lookup.adoptions(list.stream().map(FarmShipmentDO::getAdoptionId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(adoptions.values().stream().map(FarmAdoptionDO::getAssetId).collect(Collectors.toList()));
        List<FarmShipmentRespVO> out = new ArrayList<>();
        for (FarmShipmentDO s : list) {
            FarmShipmentRespVO v = BeanUtils.toBean(s, FarmShipmentRespVO.class);
            FarmMemberDO m = s.getMemberId() == null ? null : members.get(s.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            FarmAdoptionDO ad = s.getAdoptionId() == null ? null : adoptions.get(s.getAdoptionId());
            FarmAssetDO a = ad == null || ad.getAssetId() == null ? null : assets.get(ad.getAssetId());
            v.setAssetCode(a == null ? "" : a.getCode());
            v.setBatchNo(batchNo(s));
            v.setPlanDate(s.getCreateTime() == null ? null : s.getCreateTime().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            v.setProgress(s.getStatus() == null ? 0 : s.getStatus() == 20 ? 100 : s.getStatus() == 10 ? 62 : 28);
            out.add(v);
        }
        return out;
    }

    private static Map<String, Object> track(long time, String content) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("time", time);
        t.put("content", content);
        return t;
    }

    /** 等价于 JS 的 a || b || ''：空串/null 回退到下一个 */
    private static String firstNonEmpty(String a, String b) {
        if (StrUtil.isNotEmpty(a)) return a;
        return b == null ? "" : b;
    }
}
