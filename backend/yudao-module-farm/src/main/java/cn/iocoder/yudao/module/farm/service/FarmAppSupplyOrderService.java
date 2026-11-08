package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderRespVO;
import cn.iocoder.yudao.module.farm.controller.app.supplyorder.vo.FarmAppSupplyOrderCreateReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.SUPPLY_NEED_ADOPTION;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.SUPPLY_NOT_RETAIL;

/** C 端农资下单、我的农资订单（mock：farm-app.ts 238–269 行；views.ts supplyOrderView） */
@Service
public class FarmAppSupplyOrderService {

    private static final Set<String> DELIVER_WAYS = Set.of("store", "express", "pickup", "apply");

    @Autowired
    private FarmSupplyMapper supplyMapper;
    @Autowired
    private FarmSupplyOrderMapper orderMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmAppSupport support;
    @Autowired
    private FarmNoGenerator noGenerator;
    @Autowired
    private FarmViewLookup lookup;

    /** mock 239–265 行 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(FarmAppSupplyOrderCreateReqVO req) {
        FarmMemberDO m = support.member();
        FarmSupplyDO spu = req.getSpuId() == null ? null : supplyMapper.selectById(req.getSpuId());
        if (spu == null || spu.getStatus() == null || spu.getStatus() != 0) throw exception(BAD_REQUEST, "农资已下架");
        if (!Boolean.TRUE.equals(spu.getRetailAllowed())) throw exception(SUPPLY_NOT_RETAIL);
        // 农资必须绑定认养地块（文档 10）
        if (req.getAdoptionId() == null || req.getAdoptionId() == 0) throw exception(SUPPLY_NEED_ADOPTION);
        FarmAdoptionDO ad = support.myAdoption(req.getAdoptionId());
        if (ad == null) throw exception(SUPPLY_NEED_ADOPTION);

        String deliverWay = req.getDeliverWay() != null && DELIVER_WAYS.contains(req.getDeliverWay()) ? req.getDeliverWay() : "store";
        String receiverName = req.getReceiverName() == null ? "" : req.getReceiverName().trim();
        if (receiverName.length() > 32) receiverName = receiverName.substring(0, 32);
        String receiverMobile = req.getReceiverMobile() == null ? "" : req.getReceiverMobile().trim();
        String address = req.getAddress() == null ? "" : req.getAddress().trim();
        if (address.length() > 200) address = address.substring(0, 200);
        if ("express".equals(deliverWay)) {
            if (receiverName.isEmpty()) throw exception(BAD_REQUEST, "请填写收货人");
            if (!receiverMobile.matches("\\d{11}")) throw exception(BAD_REQUEST, "请填写 11 位手机号");
            if (address.isEmpty()) throw exception(BAD_REQUEST, "请填写收货地址");
        }

        Double q = req.getQty();
        if (q == null || q.isNaN() || q.isInfinite() || q != Math.floor(q) || q < 1) throw exception(BAD_REQUEST, "数量至少 1 件");
        int qty = q > Integer.MAX_VALUE ? Integer.MAX_VALUE : q.intValue();
        int stock = spu.getStock() == null ? 0 : spu.getStock();
        if (qty > stock) throw exception(BAD_REQUEST, "库存只剩 " + stock + " 件");

        // 扣库存：带库存条件的更新，防并发超卖
        int rows = supplyMapper.update(null, new LambdaUpdateWrapper<FarmSupplyDO>()
                .eq(FarmSupplyDO::getId, spu.getId())
                .apply("stock >= {0}", qty)
                .setSql("stock = stock - " + qty + ", sold = COALESCE(sold, 0) + " + qty));
        if (rows == 0) throw exception(BAD_REQUEST, "库存不足，请刷新后重试");

        long price = spu.getPrice() == null ? 0L : spu.getPrice();
        FarmSupplyOrderDO o = new FarmSupplyOrderDO();
        o.setNo(noGenerator.next("NZ"));
        o.setMemberId(m.getId());
        o.setAdoptionId(ad.getId());
        o.setAssetId(ad.getAssetId());
        o.setSpuId(spu.getId());
        o.setQty(qty);
        o.setAmount(price * qty);
        o.setDeliverWay(deliverWay);
        o.setReceiverName(receiverName);
        o.setReceiverMobile(receiverMobile);
        o.setAddress(address);
        o.setBatchNo(spu.getBatchNo());
        // 代管服务、保险类没有「施用」动作，直接视为已留痕
        o.setTraceState("svc".equals(spu.getCat()) || "ins".equals(spu.getCat()) ? 10 : 0);
        o.setUsedAt(null);
        o.setDosage(null);
        orderMapper.insert(o);

        // 代管服务、生物防治类：自动生成一张待接单的代管工单（文档 10 need_workorder）
        if (Boolean.TRUE.equals(spu.getNeedWorkorder())) {
            Map<String, Object> split = new LinkedHashMap<>();
            split.put("steward", 70);
            split.put("farm", 20);
            split.put("platform", 10);
            FarmServiceOrderDO so = new FarmServiceOrderDO();
            so.setNo(noGenerator.next("DG"));
            so.setFromTaskId(null);
            so.setTaskTitle(spu.getName());
            so.setAdoptionId(ad.getId());
            so.setAssetId(ad.getAssetId());
            so.setMemberId(m.getId());
            so.setStewardId(null);
            so.setServiceType(spu.getName() == null ? "" : spu.getName().replaceAll("（.*?）", ""));
            so.setPrice(price * qty);
            so.setStatus(0);
            so.setSplit(split);
            so.setProofPhotos(new ArrayList<>());
            so.setRemark("农资订单 " + o.getNo() + " 自动生成");
            so.setAcceptTime(null);
            so.setFinishTime(null);
            serviceOrderMapper.insert(so);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", o.getId());
        out.put("no", o.getNo());
        out.put("amount", o.getAmount());
        return out;
    }

    /** mock 266–269 行：我的农资订单，新的在前 */
    public List<FarmSupplyOrderRespVO> list() {
        Long memberId = support.memberId();
        List<FarmSupplyOrderDO> list = orderMapper.selectList(new LambdaQueryWrapper<FarmSupplyOrderDO>()
                .eq(FarmSupplyOrderDO::getMemberId, memberId)
                .orderByDesc(FarmSupplyOrderDO::getCreateTime).orderByDesc(FarmSupplyOrderDO::getId));
        List<FarmSupplyOrderRespVO> out = new ArrayList<>();
        if (list.isEmpty()) return out;
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmSupplyOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmSupplyOrderDO::getAssetId).collect(Collectors.toList()));
        List<Long> spuIds = list.stream().map(FarmSupplyOrderDO::getSpuId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, FarmSupplyDO> spus = spuIds.isEmpty() ? new java.util.HashMap<>()
                : supplyMapper.selectList(FarmSupplyDO::getId, spuIds).stream()
                .collect(Collectors.toMap(FarmSupplyDO::getId, s -> s, (a, b) -> a));
        for (FarmSupplyOrderDO o : list) {
            FarmSupplyOrderRespVO v = BeanUtils.toBean(o, FarmSupplyOrderRespVO.class);
            FarmMemberDO mm = o.getMemberId() == null ? null : members.get(o.getMemberId());
            FarmAssetDO a = o.getAssetId() == null ? null : assets.get(o.getAssetId());
            FarmSupplyDO s = o.getSpuId() == null ? null : spus.get(o.getSpuId());
            v.setMemberName(mm == null || mm.getNickname() == null ? "" : mm.getNickname());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            v.setSpuName(s == null || s.getName() == null ? "" : s.getName());
            v.setCat(s == null || s.getCat() == null ? "" : s.getCat());
            v.setCover(s == null || s.getCover() == null ? "" : s.getCover());
            v.setUnit(s == null || s.getUnit() == null ? "" : s.getUnit());
            out.add(v);
        }
        return out;
    }
}
