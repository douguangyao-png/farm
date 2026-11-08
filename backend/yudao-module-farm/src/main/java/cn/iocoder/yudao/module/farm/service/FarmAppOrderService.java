package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderContractVO;
import cn.iocoder.yudao.module.farm.controller.admin.order.vo.FarmOrderRespVO;
import cn.iocoder.yudao.module.farm.controller.app.order.vo.FarmAppOrderCreateReqVO;
import cn.iocoder.yudao.module.farm.controller.app.order.vo.FarmAppOrderDetailRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmShipmentDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmStewardDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmShipmentMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmStewardMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.ORDER_CONTRACT_NOT_AGREED;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.ORDER_NO_FREE_ASSET;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.ORDER_STATUS_CHANGED;

/** C 端认养下单、支付、订单查询（mock：farm-app.ts 60–144 行；views.ts orderView / contractOf） */
@Service
public class FarmAppOrderService {

    /** 自管任务模板：标题、说明、下发后多少小时到期、委托代办价（分） */
    private record SelfTask(String title, String description, int afterHours, long price) {
    }

    /** 按托管方式生成自管任务：全托管 0 项，半托管 2 项，不托管 5 项（文档 10「每周待办」；R-4） */
    private static final Map<String, List<SelfTask>> SELF_TASKS = Map.of(
            "full", List.of(),
            "semi", List.of(
                    new SelfTask("浇水", "新地块，先浇一次透水。半托管下浇水由您负责。", 24, 1500),
                    new SelfTask("除草", "行间杂草清一遍。", 72, 3000)),
            "self", List.of(
                    new SelfTask("整地", "翻地、起垄，农场可借用工具。", 24, 6000),
                    new SelfTask("播种", "按种植计划播种，种子可在农资商城购买。", 48, 4000),
                    new SelfTask("浇水", "播种后浇透水。", 50, 1500),
                    new SelfTask("除草", "行间杂草清一遍。", 96, 3000),
                    new SelfTask("追肥", "每株追施有机肥一把。", 120, 2000)));

    private static final Pattern PROMISED_JIN = Pattern.compile("约定交付[^\\d]*(\\d+(?:\\.\\d+)?)\\s*斤");

    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmProductMapper productMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmStewardMapper stewardMapper;
    @Autowired
    private FarmShipmentMapper shipmentMapper;
    @Autowired
    private AdminUserApi adminUserApi;
    @Autowired
    private FarmAppSupport support;
    @Autowired
    private FarmNoGenerator noGenerator;
    @Autowired
    private FarmViewLookup lookup;

    /** mock 61–76 行 */
    public Map<String, Object> create(FarmAppOrderCreateReqVO req) {
        FarmMemberDO m = support.member();
        FarmProductDO p = req.getProductId() == null ? null : productMapper.selectById(req.getProductId());
        if (p == null || p.getStatus() == null || p.getStatus() != 0) throw exception(BAD_REQUEST, "商品已下架");
        Map<String, Object> mode = findMode(p, req.getCareMode(), true);
        if (mode == null) throw exception(BAD_REQUEST, "请选择托管方式");
        if (!Boolean.TRUE.equals(req.getContractAgreed())) throw exception(ORDER_CONTRACT_NOT_AGREED);

        // 第一期补齐：渠道、交付方式、定位。缺省 channel=h5、deliverWay=express，传了不认识的值 400
        String channel = StrUtil.isEmpty(req.getChannel()) ? "h5" : req.getChannel();
        if (!List.of("mini", "h5", "scan", "steward").contains(channel)) throw exception(BAD_REQUEST, "来源渠道不正确");
        String deliverWay = StrUtil.isEmpty(req.getDeliverWay()) ? "express" : req.getDeliverWay();
        if (!List.of("express", "pickup", "farm_deliver").contains(deliverWay)) throw exception(BAD_REQUEST, "交付方式不正确");
        BigDecimal lng = req.getLng();
        BigDecimal lat = req.getLat();
        if ((lng != null && (lng.compareTo(BigDecimal.valueOf(-180)) < 0 || lng.compareTo(BigDecimal.valueOf(180)) > 0))
                || (lat != null && (lat.compareTo(BigDecimal.valueOf(-90)) < 0 || lat.compareTo(BigDecimal.valueOf(90)) > 0))) {
            throw exception(BAD_REQUEST, "定位坐标不正确");
        }

        FarmOrderDO o = new FarmOrderDO();
        o.setChannel(channel);
        o.setDeliverWay(deliverWay);
        String pkg = req.getPackageName() == null ? "" : req.getPackageName().trim();
        if (pkg.length() > 64) pkg = pkg.substring(0, 64);
        o.setPackageName(pkg.isEmpty() ? FarmViewLookup.careModeName(String.valueOf(mode.get("mode"))) + " · "
                + (p.getCycleDays() != null && p.getCycleDays() >= 360 ? "一年" : p.getCycleDays() + " 天") : pkg);
        o.setCouponAmount(0L);
        o.setGpsLng(lng);
        o.setGpsLat(lat);
        o.setGpsText(req.getGpsText() == null ? null : (req.getGpsText().length() > 128 ? req.getGpsText().substring(0, 128) : req.getGpsText()));
        o.setNo(noGenerator.next("RY"));
        o.setMemberId(m.getId());
        o.setProductId(p.getId());
        o.setCareMode(String.valueOf(mode.get("mode")));
        o.setAmount(toLong(mode.get("price")));
        o.setStatus(0);
        o.setAssetId(null);
        o.setAdoptionId(null);
        o.setContractVersion(mode.get("contractVersion") == null ? "" : String.valueOf(mode.get("contractVersion")));
        o.setContractAgreed(true);
        o.setReceiverName(StrUtil.isNotEmpty(req.getReceiverName()) ? req.getReceiverName() : m.getNickname());
        o.setReceiverMobile(StrUtil.isNotEmpty(req.getReceiverMobile()) ? req.getReceiverMobile() : m.getMobile());
        o.setAddress(StrUtil.isNotEmpty(req.getAddress()) ? req.getAddress() : (m.getAddress() == null ? "" : m.getAddress()));
        o.setPayTime(null);
        orderMapper.insert(o);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", o.getId());
        out.put("no", o.getNo());
        out.put("amount", o.getAmount());
        return out;
    }

    /**
     * 演示支付（mock 99–133 行）：分配空闲资产、生成认养关系、订单置为认养中、生成自管任务和管家交接任务。
     * 整体在一个事务里；资产、订单都用「带状态条件的更新」防并发重复分配、重复支付。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pay(Long orderId) {
        FarmMemberDO m = support.member();
        FarmOrderDO o = orderId == null ? null : orderMapper.selectById(orderId);
        if (o == null || !Objects.equals(o.getMemberId(), m.getId())) throw exception(NOT_FOUND, "订单");
        if (o.getStatus() == null || o.getStatus() != 0) throw exception(ORDER_STATUS_CHANGED);
        FarmProductDO p = o.getProductId() == null ? null : productMapper.selectById(o.getProductId());
        if (p == null) throw exception(NOT_FOUND, "认养商品");

        // 1. 分配空闲资产：同基地、同类型、空闲（status=0），按编号顺序取第一个；用条件更新抢占
        List<FarmAssetDO> candidates = assetMapper.selectList(new LambdaQueryWrapper<FarmAssetDO>()
                .eq(FarmAssetDO::getBaseId, p.getBaseId())
                .eq(FarmAssetDO::getAssetType, p.getAssetType())
                .eq(FarmAssetDO::getStatus, 0)
                .orderByAsc(FarmAssetDO::getId)
                .last("LIMIT 20"));
        FarmAssetDO asset = null;
        for (FarmAssetDO c : candidates) {
            int rows = assetMapper.update(null, new LambdaUpdateWrapper<FarmAssetDO>()
                    .eq(FarmAssetDO::getId, c.getId())
                    .eq(FarmAssetDO::getStatus, 0)
                    .set(FarmAssetDO::getStatus, 10));
            if (rows > 0) {
                asset = c;
                break;
            }
        }
        if (asset == null) throw exception(ORDER_NO_FREE_ASSET);

        // 2. 管家：优先同基地的，其次本租户任一管家（farm_steward.userId 是芋道后台用户编号）
        Long stewardUserId = findStewardUserId(p.getBaseId());

        // 3. 认养关系
        Map<String, Object> mode = findMode(p, o.getCareMode(), false);
        LocalDateTime now = LocalDateTime.now();
        int cycleDays = p.getCycleDays() == null ? 365 : p.getCycleDays();
        FarmAdoptionDO ad = new FarmAdoptionDO();
        ad.setOrderId(o.getId());
        ad.setMemberId(m.getId());
        ad.setProductId(p.getId());
        ad.setAssetId(asset.getId());
        ad.setCareMode(o.getCareMode());
        ad.setStartAt(now);
        ad.setEndAt(now.plusDays(cycleDays));
        ad.setStatus(10);
        ad.setPromisedJin("full".equals(o.getCareMode()) && mode != null
                ? promisedJinOf(mode.get("outputPromise") == null ? null : String.valueOf(mode.get("outputPromise"))) : null);
        ad.setHarvestedJin(BigDecimal.ZERO);
        ad.setStewardId(stewardUserId == null ? 0L : stewardUserId);
        adoptionMapper.insert(ad);

        // 4. 订单：只有待付款能变认养中，防重复支付
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<FarmOrderDO>()
                .eq(FarmOrderDO::getId, o.getId())
                .eq(FarmOrderDO::getStatus, 0)
                .set(FarmOrderDO::getStatus, 10)
                .set(FarmOrderDO::getPayTime, now)
                .set(FarmOrderDO::getAssetId, asset.getId())
                .set(FarmOrderDO::getAdoptionId, ad.getId()));
        if (orderRows == 0) throw exception(ORDER_STATUS_CHANGED); // 抛异常，事务回滚，资产和认养记录一并撤销

        // 5. 商品已售 +1、库存 -1（不小于 0）
        productMapper.update(null, new LambdaUpdateWrapper<FarmProductDO>()
                .eq(FarmProductDO::getId, p.getId())
                .setSql("sold = COALESCE(sold, 0) + 1, stock = GREATEST(COALESCE(stock, 0) - 1, 0)"));

        // 6. 自管任务（按托管方式）和管家交接任务
        for (SelfTask t : SELF_TASKS.getOrDefault(o.getCareMode(), List.of())) {
            FarmTaskDO task = new FarmTaskDO();
            task.setAdoptionId(ad.getId());
            task.setAssetId(asset.getId());
            task.setOwner("member");
            task.setStewardId(null);
            task.setTitle(t.title());
            task.setDescription(t.description());
            task.setSource("calendar");
            task.setServiceOrderId(null);
            task.setDeadline(now.plusHours(t.afterHours()));
            task.setStatus(0);
            task.setEntrustPrice(t.price());
            task.setFinishTime(null);
            taskMapper.insert(task);
        }
        if (stewardUserId != null) {
            FarmTaskDO task = new FarmTaskDO();
            task.setAdoptionId(ad.getId());
            task.setAssetId(asset.getId());
            task.setOwner("steward");
            task.setStewardId(stewardUserId);
            task.setTitle(asset.getCode() + " 新认养交接");
            task.setDescription(m.getNickname() + "认养了 " + asset.getCode() + "（" + FarmViewLookup.careModeName(o.getCareMode())
                    + "），请插认养牌并拍照。");
            task.setSource("manual");
            task.setServiceOrderId(null);
            task.setDeadline(now.plusHours(24));
            task.setStatus(0);
            task.setEntrustPrice(null);
            task.setFinishTime(null);
            taskMapper.insert(task);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", o.getId());
        out.put("adoptionId", ad.getId());
        out.put("assetCode", asset.getCode());
        return out;
    }

    /** mock 135–140 行：订单视图 + 合同 + 认养摘要 */
    public FarmAppOrderDetailRespVO get(Long id) {
        FarmMemberDO m = support.member();
        FarmOrderDO o = id == null ? null : orderMapper.selectById(id);
        if (o == null || !Objects.equals(o.getMemberId(), m.getId())) throw exception(NOT_FOUND, "订单");
        return appViews(List.of(o)).get(0);
    }

    /** mock 141–144 行：我的订单，新的在前；每条带合同和认养摘要（合约中心、我的订单页用） */
    public List<FarmAppOrderDetailRespVO> list() {
        FarmMemberDO m = support.member();
        List<FarmOrderDO> list = orderMapper.selectList(new LambdaQueryWrapper<FarmOrderDO>()
                .eq(FarmOrderDO::getMemberId, m.getId())
                .orderByDesc(FarmOrderDO::getCreateTime).orderByDesc(FarmOrderDO::getId));
        return appViews(list);
    }

    // ---------- helpers ----------

    private List<FarmOrderRespVO> toViews(List<FarmOrderDO> list) {
        List<FarmOrderRespVO> out = new ArrayList<>();
        if (list.isEmpty()) return out;
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, FarmProductDO> products = lookup.products(list.stream().map(FarmOrderDO::getProductId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmOrderDO::getAssetId).collect(Collectors.toList()));
        for (FarmOrderDO o : list) {
            FarmOrderRespVO v = BeanUtils.toBean(o, FarmOrderRespVO.class);
            FarmMemberDO mm = o.getMemberId() == null ? null : members.get(o.getMemberId());
            FarmProductDO pp = o.getProductId() == null ? null : products.get(o.getProductId());
            FarmAssetDO aa = o.getAssetId() == null ? null : assets.get(o.getAssetId());
            v.setMemberName(mm == null || mm.getNickname() == null ? "" : mm.getNickname());
            v.setMemberMobile(mm == null || mm.getMobile() == null ? "" : mm.getMobile());
            v.setProductTitle(pp == null || pp.getTitle() == null ? "" : pp.getTitle());
            v.setProductCover(pp == null || pp.getCover() == null ? "" : pp.getCover());
            v.setAssetCode(aa == null || aa.getCode() == null ? "" : aa.getCode());
            v.setCareModeName(FarmViewLookup.careModeName(o.getCareMode()));
            if (v.getChannel() == null) v.setChannel("h5");
            if (v.getDeliverWay() == null) v.setDeliverWay("express");
            if (v.getCouponAmount() == null) v.setCouponAmount(0L);
            out.add(v);
        }
        return out;
    }

    /** 对应 mock appOrderView：订单视图 + 合同 + 认养摘要（周期、管家、编号、保单号、溯源码为演示口径） */
    private List<FarmAppOrderDetailRespVO> appViews(List<FarmOrderDO> list) {
        List<FarmAppOrderDetailRespVO> out = new ArrayList<>();
        if (list.isEmpty()) return out;
        List<FarmOrderRespVO> base = toViews(list);
        Map<Long, FarmAdoptionDO> adoptions = lookup.adoptions(list.stream().map(FarmOrderDO::getAdoptionId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(adoptions.values().stream().map(FarmAdoptionDO::getAssetId).collect(Collectors.toList()));
        for (int i = 0; i < list.size(); i++) {
            FarmOrderDO o = list.get(i);
            FarmAppOrderDetailRespVO d = BeanUtils.toBean(base.get(i), FarmAppOrderDetailRespVO.class);
            String no = o.getNo() == null ? "" : o.getNo();
            FarmOrderContractVO c = new FarmOrderContractVO();
            c.setNo("HT" + (no.length() > 2 ? no.substring(2) : ""));
            c.setVersion(o.getContractVersion());
            c.setTitle("《认养服务合同（" + FarmViewLookup.careModeName(o.getCareMode()) + "）》" + o.getContractVersion());
            c.setAgreed(o.getContractAgreed());
            c.setSignedAt(o.getSignedAt() != null ? o.getSignedAt() : o.getPayTime());
            c.setHasSignature(StrUtil.isNotEmpty(o.getSignature()));
            d.setContract(c);

            FarmAdoptionDO ad = o.getAdoptionId() == null ? null : adoptions.get(o.getAdoptionId());
            // 与 FarmOrderService.toViews 同口径：支付方式固定微信支付；履约进度 = 已收 / 约定交付
            BigDecimal promised = ad == null ? null : ad.getPromisedJin();
            BigDecimal harvested = ad == null || ad.getHarvestedJin() == null ? BigDecimal.ZERO : ad.getHarvestedJin();
            d.setPayMethod("微信支付");
            d.setPromisedJin(promised);
            d.setHarvestedJin(harvested);
            d.setProgress(promised == null || promised.signum() <= 0 ? 0
                    : Math.min(100, harvested.multiply(BigDecimal.valueOf(100)).divide(promised, 0, RoundingMode.HALF_UP).intValue()));

            d.setShipmentCount(0L);
            d.setAssetName("");
            d.setStewardName("");
            d.setStewardMobile("");
            if (ad != null) {
                LocalDateTime t = o.getPayTime() != null ? o.getPayTime() : o.getCreateTime();
                int year = t == null ? LocalDateTime.now().getYear() : t.getYear();
                d.setAdoptionNo(String.format("A%d-%04d", year, ad.getId()));
                d.setAdoptionStatus(ad.getStatus());
                d.setStartAt(ad.getStartAt());
                d.setEndAt(ad.getEndAt());
                d.setShipmentCount(shipmentMapper.selectCount(new LambdaQueryWrapper<FarmShipmentDO>()
                        .eq(FarmShipmentDO::getAdoptionId, ad.getId())));
                FarmAssetDO a = ad.getAssetId() == null ? null : assets.get(ad.getAssetId());
                if (a != null) {
                    d.setAssetName(a.getName() == null ? "" : a.getName());
                    d.setAreaM2(a.getSpecs() == null ? null : a.getSpecs().get("areaM2"));
                }
                AdminUserRespDTO st = ad.getStewardId() == null || ad.getStewardId() <= 0 ? null : adminUserApi.getUser(ad.getStewardId());
                if (st != null) {
                    d.setStewardName(st.getNickname() == null ? "" : st.getNickname());
                    d.setStewardMobile(maskMobile(st.getMobile()));
                }
                d.setInsuranceNo(String.format("PICC-%d-%04d", year, o.getId()));
                d.setTraceCode("TR" + (no.length() > 2 ? no.substring(2) : ""));
            }
            out.add(d);
        }
        return out;
    }

    /** 手机号脱敏：138****1234（mock maskMobile） */
    private static String maskMobile(String mobile) {
        if (mobile == null) return "";
        return mobile.replaceFirst("^(\\d{3})\\d{4}(\\d{4})$", "$1****$2");
    }

    private Long findStewardUserId(Long baseId) {
        List<FarmStewardDO> same = stewardMapper.selectList(new LambdaQueryWrapper<FarmStewardDO>()
                .eq(FarmStewardDO::getBaseId, baseId).orderByAsc(FarmStewardDO::getId).last("LIMIT 1"));
        if (!same.isEmpty()) return same.get(0).getUserId();
        List<FarmStewardDO> any = stewardMapper.selectList(new LambdaQueryWrapper<FarmStewardDO>()
                .orderByAsc(FarmStewardDO::getId).last("LIMIT 1"));
        return any.isEmpty() ? null : any.get(0).getUserId();
    }

    private static Map<String, Object> findMode(FarmProductDO p, String mode, boolean mustEnabled) {
        if (p.getCareModes() == null || mode == null) return null;
        for (Map<String, Object> m : p.getCareModes()) {
            if (m != null && mode.equals(m.get("mode")) && (!mustEnabled || Boolean.TRUE.equals(m.get("enabled")))) return m;
        }
        return null;
    }

    private static BigDecimal promisedJinOf(String text) {
        if (text == null) return null;
        Matcher mt = PROMISED_JIN.matcher(text);
        return mt.find() ? new BigDecimal(mt.group(1)) : null;
    }

    private static long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o == null) return 0L;
        try {
            return (long) Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
