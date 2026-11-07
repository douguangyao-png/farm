package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.supplyorder.vo.FarmSupplyOrderTraceReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmInputRecordDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmInputRecordMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.TASK_STATUS_CHANGED;

/** 农资订单与登记施用（契约 2.8；mock 见 farm-admin.ts 335-353、views.ts supplyOrderView） */
@Service
public class FarmSupplyOrderService {

    @Autowired
    private FarmSupplyOrderMapper orderMapper;
    @Autowired
    private FarmSupplyMapper supplyMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmInputRecordMapper inputRecordMapper;

    /** 列表 + 统计卡（stats 字段见契约 2.8；mock：farm/stats.ts supplyOrderPage）。统计看全租户，所以先全量拼装再在内存里过滤、分页 */
    public FarmFulfillPage<FarmSupplyOrderRespVO> page(FarmSupplyOrderPageReqVO req) {
        List<FarmSupplyOrderDO> raw = orderMapper.selectList(new LambdaQueryWrapperX<FarmSupplyOrderDO>()
                .orderByDesc(FarmSupplyOrderDO::getCreateTime)
                .orderByDesc(FarmSupplyOrderDO::getId));
        List<FarmSupplyOrderRespVO> all = toViews(raw);

        LocalDateTime d0 = LocalDate.now().atStartOfDay();
        long todayCount = 0;
        long todayAmount = 0;
        Map<Long, Integer> perMember = new HashMap<>();
        for (FarmSupplyOrderRespVO o : all) {
            if (o.getCreateTime() != null && !o.getCreateTime().isBefore(d0)) {
                todayCount++;
                todayAmount += o.getAmount() == null ? 0L : o.getAmount();
            }
            perMember.merge(o.getMemberId(), 1, Integer::sum);
        }
        long repeatMemberCount = perMember.values().stream().filter(n -> n >= 2).count();
        Map<String, Object> stats = FarmFulfillPage.m(
                "todayCount", todayCount,
                "todayAmount", todayAmount,
                "pendingDispatch", all.stream().filter(o -> "待派单".equals(o.getStateName())).count(),
                "untraced", all.stream().filter(o -> o.getTraceState() != null && o.getTraceState() == 0).count(),
                "repeatMemberCount", repeatMemberCount,
                "repeatRate", perMember.isEmpty() ? 0d : FarmFulfillPage.r1(repeatMemberCount * 100d / perMember.size()));

        List<FarmSupplyOrderRespVO> rows = all.stream()
                .filter(o -> req.getTraceState() == null || req.getTraceState().equals(o.getTraceState()))
                .filter(o -> StrUtil.isEmpty(req.getNo()) || (o.getNo() != null && o.getNo().contains(req.getNo())))
                .filter(o -> StrUtil.isEmpty(req.getKeyword())
                        || (o.getNo() != null && o.getNo().contains(req.getKeyword()))
                        || (o.getMemberName() != null && o.getMemberName().contains(req.getKeyword())))
                .filter(o -> StrUtil.isEmpty(req.getAssetCode()) || (o.getAssetCode() != null && o.getAssetCode().contains(req.getAssetCode())))
                .filter(o -> StrUtil.isEmpty(req.getState()) || req.getState().equals(o.getStateName()))
                .collect(Collectors.toList());
        FarmFulfillPage<FarmSupplyOrderRespVO> out = new FarmFulfillPage<>(rows, req, stats);
        return out;
    }

    /**
     * 订单状态（演示口径，库里没有单独状态字段，与 mock supplyOrderStateName 一致）：
     * 服务类 = 登记后已完成、否则待派单；保险 = 已生效；农具登记后 = 已安装；其余登记后 = 已使用、否则待使用
     */
    public static String stateName(String cat, Integer traceState) {
        boolean traced = traceState != null && traceState == 10;
        if ("svc".equals(cat)) return traced ? "已完成" : "待派单";
        if ("ins".equals(cat)) return "已生效";
        if (traced) return "tool".equals(cat) ? "已安装" : "已使用";
        return "待使用";
    }

    /** 登记施用。命中「已经登记过了」用任务状态码 1030000006（与 mock 的 TASK_STATE 一致），提示语沿用 mock */
    public void trace(FarmSupplyOrderTraceReqVO req) {
        FarmSupplyOrderDO o = req.getId() == null ? null : orderMapper.selectById(req.getId());
        if (o == null) throw exception(NOT_FOUND, "农资订单");
        if (req.getUsedAt() == null || req.getUsedAt() == 0) throw exception(BAD_REQUEST, "请填写施用时间");
        if (StrUtil.isBlank(req.getDosage())) throw exception(BAD_REQUEST, "请填写用量");
        if (o.getTraceState() != null && o.getTraceState() == 10) {
            throw new ServiceException(TASK_STATUS_CHANGED.getCode(), "已经登记过了");
        }
        LocalDateTime usedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(req.getUsedAt()), ZoneId.systemDefault());
        o.setTraceState(10);
        o.setUsedAt(usedAt);
        o.setDosage(req.getDosage());
        orderMapper.updateById(o);

        FarmSupplyDO spu = supplyMapper.selectById(o.getSpuId());
        FarmMemberDO member = o.getMemberId() == null ? null : memberMapper.selectById(o.getMemberId());
        FarmInputRecordDO r = new FarmInputRecordDO();
        r.setAssetId(o.getAssetId());
        r.setSource("member");
        r.setName(spu == null ? "" : spu.getName());
        r.setBatchNo(o.getBatchNo());
        r.setUsedAt(usedAt);
        r.setDosage(req.getDosage());
        r.setOperator(member == null ? "" : member.getNickname());
        inputRecordMapper.insert(r);
    }

    private List<FarmSupplyOrderRespVO> toViews(List<FarmSupplyOrderDO> list) {
        List<FarmSupplyOrderRespVO> out = new ArrayList<>();
        if (CollUtil.isEmpty(list)) return out;
        Set<Long> memberIds = list.stream().map(FarmSupplyOrderDO::getMemberId).collect(Collectors.toSet());
        Set<Long> assetIds = list.stream().map(FarmSupplyOrderDO::getAssetId).collect(Collectors.toSet());
        Set<Long> spuIds = list.stream().map(FarmSupplyOrderDO::getSpuId).collect(Collectors.toSet());
        Map<Long, String> memberNames = new HashMap<>();
        for (FarmMemberDO m : memberMapper.selectList(FarmMemberDO::getId, memberIds)) memberNames.put(m.getId(), m.getNickname());
        Map<Long, String> assetCodes = new HashMap<>();
        for (FarmAssetDO a : assetMapper.selectList(FarmAssetDO::getId, assetIds)) assetCodes.put(a.getId(), a.getCode());
        Map<Long, FarmSupplyDO> spus = new HashMap<>();
        for (FarmSupplyDO s : supplyMapper.selectList(FarmSupplyDO::getId, spuIds)) spus.put(s.getId(), s);

        for (FarmSupplyOrderDO o : list) {
            FarmSupplyOrderRespVO v = BeanUtils.toBean(o, FarmSupplyOrderRespVO.class);
            v.setMemberName(memberNames.getOrDefault(o.getMemberId(), ""));
            v.setAssetCode(assetCodes.getOrDefault(o.getAssetId(), ""));
            FarmSupplyDO spu = spus.get(o.getSpuId());
            v.setSpuName(spu == null ? "" : spu.getName());
            v.setCat(spu == null ? "" : spu.getCat());
            v.setCover(spu == null ? "" : spu.getCover());
            v.setUnit(spu == null ? "" : spu.getUnit());
            v.setStateName(stateName(v.getCat(), o.getTraceState()));
            out.add(v);
        }
        return out;
    }
}
