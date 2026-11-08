package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderAdoptionVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderContractVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmShipmentDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmShipmentMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 接口契约 2.5 认养订单（mock：farm-admin.ts 242–262 行；views.ts orderView / contractOf / adoptionView） */
@Service
public class FarmOrderService {

    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmShipmentMapper shipmentMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmRecordService recordService;
    @Autowired
    private FarmShipmentService shipmentService;
    @Autowired
    private FarmViewLookup lookup;
    @Autowired
    private FarmCustomerService customerService;
    @Autowired
    private AdminUserApi adminUserApi;

    public PageResult<FarmOrderRespVO> page(FarmOrderPageReqVO req) {
        LambdaQueryWrapperX<FarmOrderDO> w = new LambdaQueryWrapperX<FarmOrderDO>()
                .likeIfPresent(FarmOrderDO::getNo, req.getNo())
                .eqIfPresent(FarmOrderDO::getCareMode, req.getCareMode())
                .eqIfPresent(FarmOrderDO::getStatus, req.getStatus())
                .eqIfPresent(FarmOrderDO::getProductId, req.getProductId());
        if (req.getCreateTimeStart() != null) w.ge(FarmOrderDO::getCreateTime, toTime(req.getCreateTimeStart()));
        if (req.getCreateTimeEnd() != null) w.le(FarmOrderDO::getCreateTime, toTime(req.getCreateTimeEnd()));
        if (StrUtil.isNotEmpty(req.getMemberMobile())) {
            List<Long> memberIds = memberMapper.selectList(new LambdaQueryWrapperX<FarmMemberDO>()
                            .like(FarmMemberDO::getMobile, req.getMemberMobile()))
                    .stream().map(FarmMemberDO::getId).collect(Collectors.toList());
            if (memberIds.isEmpty()) return PageResult.empty();
            w.in(FarmOrderDO::getMemberId, memberIds);
        }
        w.orderByDesc(FarmOrderDO::getCreateTime).orderByDesc(FarmOrderDO::getId);
        PageResult<FarmOrderDO> p = orderMapper.selectPage(req, w);
        return new PageResult<>(toViews(p.getList()), p.getTotal());
    }

    public FarmOrderDetailRespVO get(Long id) {
        FarmOrderDO o = id == null ? null : orderMapper.selectById(id);
        if (o == null) throw exception(NOT_FOUND, "订单");
        FarmOrderDetailRespVO d = BeanUtils.toBean(toViews(List.of(o)).get(0), FarmOrderDetailRespVO.class);

        FarmOrderContractVO c = new FarmOrderContractVO();
        String no = o.getNo() == null ? "" : o.getNo();
        c.setNo("HT" + (no.length() > 2 ? no.substring(2) : ""));
        c.setVersion(o.getContractVersion());
        c.setTitle("《认养服务合同（" + FarmViewLookup.careModeName(o.getCareMode()) + "）》" + o.getContractVersion());
        c.setAgreed(o.getContractAgreed());
        c.setSignedAt(o.getSignedAt() != null ? o.getSignedAt() : o.getPayTime());
        c.setHasSignature(StrUtil.isNotEmpty(o.getSignature()));
        d.setContract(c);
        d.setCustomerTags(o.getMemberId() == null ? new ArrayList<>() : customerService.tagsOf(o.getMemberId()));

        // 客户信息卡：累计认养 = 该客户已支付且未取消的订单数，累计消费 = 其金额合计（分）
        FarmMemberDO mem = o.getMemberId() == null ? null : lookup.members(List.of(o.getMemberId())).get(o.getMemberId());
        List<FarmOrderDO> paid = o.getMemberId() == null ? new ArrayList<>() : orderMapper.selectList(new LambdaQueryWrapperX<FarmOrderDO>()
                .eq(FarmOrderDO::getMemberId, o.getMemberId())
                .isNotNull(FarmOrderDO::getPayTime)
                .ne(FarmOrderDO::getStatus, 30));
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", mem == null || mem.getNickname() == null ? "" : mem.getNickname());
        customer.put("mobile", mem == null || mem.getMobile() == null ? "" : mem.getMobile());
        customer.put("orderCount", paid.size());
        customer.put("totalAmount", paid.stream().mapToLong(x -> x.getAmount() == null ? 0L : x.getAmount()).sum());
        d.setCustomer(customer);

        FarmAdoptionDO ad = o.getAdoptionId() == null ? null : adoptionMapper.selectById(o.getAdoptionId());
        if (ad == null) {
            d.setAdoption(null);
            d.setRecords(new ArrayList<>());
            d.setShipments(new ArrayList<>());
            return d;
        }
        d.setAdoption(adoptionView(ad));
        AdminUserRespDTO st = ad.getStewardId() == null || ad.getStewardId() <= 0 ? null : adminUserApi.getUser(ad.getStewardId());
        if (st != null) {
            Map<String, Object> steward = new LinkedHashMap<>();
            steward.put("name", st.getNickname());
            steward.put("mobile", maskMobile(st.getMobile()));
            d.setSteward(steward);
        }
        List<FarmRecordDO> records = recordMapper.selectList(new LambdaQueryWrapperX<FarmRecordDO>()
                .eq(FarmRecordDO::getAdoptionId, ad.getId())
                .eq(FarmRecordDO::getStatus, 10)
                .orderByDesc(FarmRecordDO::getCreateTime)
                .orderByDesc(FarmRecordDO::getId)
                .last("LIMIT 10"));
        d.setRecords(recordService.toViews(records));
        List<FarmShipmentDO> shipments = shipmentMapper.selectList(new LambdaQueryWrapperX<FarmShipmentDO>()
                .eq(FarmShipmentDO::getAdoptionId, ad.getId())
                .orderByAsc(FarmShipmentDO::getId));
        d.setShipments(shipmentService.toViews(shipments));
        return d;
    }

    private List<FarmOrderRespVO> toViews(List<FarmOrderDO> list) {
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, FarmProductDO> products = lookup.products(list.stream().map(FarmOrderDO::getProductId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmOrderDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmAdoptionDO> adoptions = lookup.adoptions(list.stream().map(FarmOrderDO::getAdoptionId).collect(Collectors.toList()));
        List<FarmOrderRespVO> out = new ArrayList<>();
        for (FarmOrderDO o : list) {
            FarmOrderRespVO v = BeanUtils.toBean(o, FarmOrderRespVO.class);
            FarmMemberDO m = o.getMemberId() == null ? null : members.get(o.getMemberId());
            FarmProductDO p = o.getProductId() == null ? null : products.get(o.getProductId());
            FarmAssetDO a = o.getAssetId() == null ? null : assets.get(o.getAssetId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setMemberMobile(m == null || m.getMobile() == null ? "" : m.getMobile());
            v.setProductTitle(p == null || p.getTitle() == null ? "" : p.getTitle());
            v.setProductCover(p == null || p.getCover() == null ? "" : p.getCover());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            v.setCareModeName(FarmViewLookup.careModeName(o.getCareMode()));
            // 老订单没有这些字段时给缺省值（与 mock orderView 一致）
            if (v.getChannel() == null) v.setChannel("h5");
            if (v.getDeliverWay() == null) v.setDeliverWay("express");
            if (v.getCouponAmount() == null) v.setCouponAmount(0L);
            // 演示口径：支付方式固定微信支付；履约进度 = 已收 / 约定交付
            FarmAdoptionDO ad = o.getAdoptionId() == null ? null : adoptions.get(o.getAdoptionId());
            BigDecimal promised = ad == null ? null : ad.getPromisedJin();
            BigDecimal harvested = ad == null || ad.getHarvestedJin() == null ? BigDecimal.ZERO : ad.getHarvestedJin();
            v.setPayMethod("微信支付");
            v.setPromisedJin(promised);
            v.setHarvestedJin(harvested);
            v.setProgress(promised == null || promised.signum() <= 0 ? 0
                    : Math.min(100, harvested.multiply(BigDecimal.valueOf(100)).divide(promised, 0, RoundingMode.HALF_UP).intValue()));
            out.add(v);
        }
        return out;
    }

    private FarmOrderAdoptionVO adoptionView(FarmAdoptionDO ad) {
        FarmOrderAdoptionVO v = BeanUtils.toBean(ad, FarmOrderAdoptionVO.class);
        FarmProductDO p = ad.getProductId() == null ? null : lookup.products(List.of(ad.getProductId())).get(ad.getProductId());
        FarmAssetDO a = ad.getAssetId() == null ? null : lookup.assets(List.of(ad.getAssetId())).get(ad.getAssetId());
        FarmBaseDO b = a == null || a.getBaseId() == null ? null : lookup.bases(List.of(a.getBaseId())).get(a.getBaseId());
        FarmMemberDO m = ad.getMemberId() == null ? null : lookup.members(List.of(ad.getMemberId())).get(ad.getMemberId());
        v.setProductTitle(p == null || p.getTitle() == null ? "" : p.getTitle());
        v.setCover(p == null || p.getCover() == null ? "" : p.getCover());
        v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
        v.setAssetType(a == null || a.getAssetType() == null ? "" : a.getAssetType());
        v.setBaseName(b == null || b.getName() == null ? "" : b.getName());
        v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
        v.setCareModeName(FarmViewLookup.careModeName(ad.getCareMode()));
        v.setSelfTodoCount(taskMapper.selectCount(new LambdaQueryWrapperX<FarmTaskDO>()
                .eq(FarmTaskDO::getAdoptionId, ad.getId())
                .eq(FarmTaskDO::getOwner, "member")
                .eq(FarmTaskDO::getStatus, 0)));
        return v;
    }

    private static LocalDateTime toTime(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    /** 手机号脱敏：138****1234（mock maskMobile） */
    private static String maskMobile(String mobile) {
        if (mobile == null) return "";
        return mobile.replaceFirst("^(\\d{3})\\d{4}(\\d{4})$", "$1****$2");
    }
}
