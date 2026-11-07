package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    @Resource
    private FarmSupplyOrderMapper orderMapper;
    @Resource
    private FarmSupplyMapper supplyMapper;
    @Resource
    private FarmMemberMapper memberMapper;
    @Resource
    private FarmAssetMapper assetMapper;
    @Resource
    private FarmInputRecordMapper inputRecordMapper;

    public PageResult<FarmSupplyOrderRespVO> page(FarmSupplyOrderPageReqVO req) {
        PageResult<FarmSupplyOrderDO> p = orderMapper.selectPage(req, new LambdaQueryWrapperX<FarmSupplyOrderDO>()
                .eqIfPresent(FarmSupplyOrderDO::getTraceState, req.getTraceState())
                .likeIfPresent(FarmSupplyOrderDO::getNo, req.getNo())
                .orderByDesc(FarmSupplyOrderDO::getCreateTime)
                .orderByDesc(FarmSupplyOrderDO::getId));
        return new PageResult<>(toViews(p.getList()), p.getTotal());
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
            out.add(v);
        }
        return out;
    }
}
