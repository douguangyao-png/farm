package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplyPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplyRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplySaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 农资商品（契约 2.8；mock 见 farm-admin.ts 298-334） */
@Service
public class FarmSupplyService {

    @Autowired
    private FarmSupplyMapper supplyMapper;
    @Autowired
    private FarmSupplyOrderMapper supplyOrderMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;

    /** 列表 + 统计卡 + 类目页签数量（stats 字段见契约 2.8；mock：farm/stats.ts supplyPage） */
    public FarmFulfillPage<FarmSupplyRespVO> page(FarmSupplyPageReqVO req) {
        List<FarmSupplyDO> all = supplyMapper.selectList(new LambdaQueryWrapperX<FarmSupplyDO>().orderByAsc(FarmSupplyDO::getId));

        LocalDateTime m0 = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<FarmSupplyOrderDO> orders = supplyOrderMapper.selectList(new LambdaQueryWrapperX<FarmSupplyOrderDO>());
        List<FarmSupplyOrderDO> monthOrders = orders.stream()
                .filter(o -> o.getCreateTime() != null && !o.getCreateTime().isBefore(m0)).collect(Collectors.toList());
        Map<Long, Integer> perMember = new HashMap<>();
        for (FarmSupplyOrderDO o : orders) perMember.merge(o.getMemberId(), 1, Integer::sum);
        long repeatMemberCount = perMember.values().stream().filter(n -> n >= 2).count();
        long monthGmv = monthOrders.stream().mapToLong(o -> o.getAmount() == null ? 0L : o.getAmount()).sum();
        long serviceIncome = serviceOrderMapper.selectList(new LambdaQueryWrapperX<FarmServiceOrderDO>().eq(FarmServiceOrderDO::getStatus, 20))
                .stream()
                .filter(x -> !(x.getFinishTime() != null ? x.getFinishTime() : x.getCreateTime()).isBefore(m0))
                .mapToLong(x -> x.getPrice() == null ? 0L : x.getPrice()).sum();
        Map<String, Object> catCounts = new LinkedHashMap<>();
        catCounts.put("all", all.size());
        for (FarmSupplyDO s : all) catCounts.merge(s.getCat(), 1, (x, y) -> (Integer) x + (Integer) y);
        Map<String, Object> stats = FarmFulfillPage.m(
                "monthGmv", monthGmv,
                "monthOrderCount", monthOrders.size(),
                "avgOrderAmount", monthOrders.isEmpty() ? 0L : Math.round(monthGmv / (double) monthOrders.size()),
                "repeatMemberCount", repeatMemberCount,
                "repeatRate", perMember.isEmpty() ? 0d : FarmFulfillPage.r1(repeatMemberCount * 100d / perMember.size()),
                "serviceIncome", serviceIncome,
                "catCounts", catCounts);

        List<FarmSupplyRespVO> rows = all.stream()
                .filter(s -> StrUtil.isEmpty(req.getName()) || (s.getName() != null && s.getName().contains(req.getName())))
                .filter(s -> StrUtil.isEmpty(req.getCat()) || req.getCat().equals(s.getCat()))
                .filter(s -> StrUtil.isEmpty(req.getBatchNo()) || (s.getBatchNo() != null && s.getBatchNo().contains(req.getBatchNo())))
                .filter(s -> req.getStatus() == null || req.getStatus().equals(s.getStatus()))
                .map(FarmSupplyService::toView)
                .collect(Collectors.toList());
        return new FarmFulfillPage<>(rows, req, stats);
    }

    /** 低库存线：库存低于 200 算低库存（服务、保险库存是 9999，不会命中） */
    private static final int LOW_STOCK = 200;

    /** DO → 列表行，补 code / grossMargin / stateName（与 mock farm/stats.ts supplyPage 一致） */
    public static FarmSupplyRespVO toView(FarmSupplyDO s) {
        FarmSupplyRespVO v = BeanUtils.toBean(s, FarmSupplyRespVO.class);
        v.setCode("SP-" + (100 + s.getId()));
        long price = s.getPrice() == null ? 0L : s.getPrice();
        long cost = s.getCost() == null ? 0L : s.getCost();
        v.setGrossMargin(price > 0 ? (int) Math.round((price - cost) * 100d / price) : 0);
        String state = "正常";
        if (s.getStatus() != null && s.getStatus() == 1) state = "已下架";
        else if (Boolean.TRUE.equals(s.getNeedWorkorder())) state = "需工单";
        else if (s.getStock() != null && s.getStock() < LOW_STOCK) state = "低库存";
        v.setStateName(state);
        return v;
    }

    public FarmSupplyDO get(Long id) {
        FarmSupplyDO s = id == null ? null : supplyMapper.selectById(id);
        if (s == null) throw exception(NOT_FOUND, "农资");
        return s;
    }

    public Long create(FarmSupplySaveReqVO req) {
        if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "名称不能为空");
        if (StrUtil.isEmpty(req.getCat())) throw exception(BAD_REQUEST, "请选择类目");
        if (req.getPrice() == null || req.getPrice() <= 0) throw exception(BAD_REQUEST, "价格要大于 0");
        checkWords(req);

        FarmSupplyDO s = new FarmSupplyDO();
        s.setName(req.getName().trim());
        s.setCat(req.getCat());
        s.setCover(req.getCover() == null ? "" : req.getCover());
        s.setPrice(req.getPrice());
        s.setCost(req.getCost() == null ? 0L : req.getCost());
        s.setStock(req.getStock() == null ? 0 : req.getStock());
        s.setUnit(req.getUnit() == null ? "件" : req.getUnit());
        s.setSpec(req.getSpec() == null ? "" : req.getSpec());
        s.setFitModes(req.getFitModes() == null ? new ArrayList<>(Arrays.asList("semi", "self")) : req.getFitModes());
        s.setBatchNo(req.getBatchNo() == null ? "" : req.getBatchNo());
        s.setRetailAllowed(!Boolean.FALSE.equals(req.getRetailAllowed()));
        s.setNeedWorkorder(Boolean.TRUE.equals(req.getNeedWorkorder()));
        s.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        s.setSold(0);
        s.setDescription(req.getDescription() == null ? "" : req.getDescription());
        normalize(s);
        supplyMapper.insert(s);
        return s.getId();
    }

    public void update(FarmSupplySaveReqVO req) {
        FarmSupplyDO s = get(req.getId());
        checkWords(req);
        if (req.getName() != null) s.setName(req.getName());
        if (req.getCat() != null) s.setCat(req.getCat());
        if (req.getCover() != null) s.setCover(req.getCover());
        if (req.getPrice() != null) s.setPrice(req.getPrice());
        if (req.getCost() != null) s.setCost(req.getCost());
        if (req.getStock() != null) s.setStock(req.getStock());
        if (req.getUnit() != null) s.setUnit(req.getUnit());
        if (req.getSpec() != null) s.setSpec(req.getSpec());
        if (req.getFitModes() != null) s.setFitModes(req.getFitModes());
        if (req.getBatchNo() != null) s.setBatchNo(req.getBatchNo());
        if (req.getRetailAllowed() != null) s.setRetailAllowed(req.getRetailAllowed());
        if (req.getNeedWorkorder() != null) s.setNeedWorkorder(req.getNeedWorkorder());
        if (req.getStatus() != null) s.setStatus(req.getStatus());
        if (req.getDescription() != null) s.setDescription(req.getDescription());
        normalize(s);
        supplyMapper.updateById(s);
    }

    /** 名称、描述里不能有金融类违禁词（农资不记命中记录） */
    private static void checkWords(FarmSupplySaveReqVO req) {
        List<String> words = FarmComplianceService.findBannedWords(Arrays.asList(req.getName(), req.getDescription()));
        if (!words.isEmpty()) throw new FarmBannedWordsException(words);
    }

    /** 农药类：不能零售，必须走工单 */
    private static void normalize(FarmSupplyDO s) {
        if ("pesticide".equals(s.getCat())) {
            s.setRetailAllowed(false);
            s.setNeedWorkorder(true);
        }
    }
}
