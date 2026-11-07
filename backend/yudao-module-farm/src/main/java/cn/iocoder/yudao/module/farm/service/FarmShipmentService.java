package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 接口契约 2.9 配送（mock：farm-admin.ts 387–419 行） */
@Service
public class FarmShipmentService {

    /** 契约 1_030_000_002：订单/单据状态已变化（提示语由调用处传入，与 mock 一致） */
    private static final ErrorCode SHIPMENT_STATE = new ErrorCode(1_030_000_002, "{}");

    @Resource
    private FarmShipmentMapper shipmentMapper;
    @Resource
    private FarmHarvestMapper harvestMapper;
    @Resource
    private FarmAdoptionMapper adoptionMapper;
    @Resource
    private FarmOrderMapper orderMapper;
    @Resource
    private FarmMemberMapper memberMapper;
    @Resource
    private FarmAssetMapper assetMapper;
    @Resource
    private FarmBaseMapper baseMapper;
    @Resource
    private FarmNoGenerator noGenerator;
    @Resource
    private FarmViewLookup lookup;

    public PageResult<FarmShipmentRespVO> page(FarmShipmentPageReqVO req) {
        PageResult<FarmShipmentDO> p = shipmentMapper.selectPage(req, new LambdaQueryWrapperX<FarmShipmentDO>()
                .eqIfPresent(FarmShipmentDO::getStatus, req.getStatus())
                .likeIfPresent(FarmShipmentDO::getNo, req.getNo())
                .orderByDesc(FarmShipmentDO::getCreateTime)
                .orderByDesc(FarmShipmentDO::getId));
        return new PageResult<>(toViews(p.getList()), p.getTotal());
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
