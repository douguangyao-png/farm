package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo.FarmServiceOrderRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 接口契约 2.7 代管工单（mock：farm-admin.ts 287–296 行） */
@Service
public class FarmServiceOrderService {

    @Resource
    private FarmServiceOrderMapper serviceOrderMapper;
    @Resource
    private FarmViewLookup lookup;

    public PageResult<FarmServiceOrderRespVO> page(FarmServiceOrderPageReqVO req) {
        PageResult<FarmServiceOrderDO> p = serviceOrderMapper.selectPage(req, new LambdaQueryWrapperX<FarmServiceOrderDO>()
                .eqIfPresent(FarmServiceOrderDO::getStatus, req.getStatus())
                .likeIfPresent(FarmServiceOrderDO::getNo, req.getNo())
                .orderByDesc(FarmServiceOrderDO::getCreateTime)
                .orderByDesc(FarmServiceOrderDO::getId));
        return new PageResult<>(toViews(p.getList()), p.getTotal());
    }

    public FarmServiceOrderRespVO get(Long id) {
        FarmServiceOrderDO s = id == null ? null : serviceOrderMapper.selectById(id);
        if (s == null) throw exception(NOT_FOUND, "代管工单");
        return toViews(List.of(s)).get(0);
    }

    private List<FarmServiceOrderRespVO> toViews(List<FarmServiceOrderDO> list) {
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmServiceOrderDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmServiceOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, String> users = lookup.userNames(list.stream().map(FarmServiceOrderDO::getStewardId).collect(Collectors.toList()));
        List<FarmServiceOrderRespVO> out = new ArrayList<>();
        for (FarmServiceOrderDO s : list) {
            FarmServiceOrderRespVO v = BeanUtils.toBean(s, FarmServiceOrderRespVO.class);
            FarmAssetDO a = s.getAssetId() == null ? null : assets.get(s.getAssetId());
            v.setAssetCode(a == null ? "" : a.getCode());
            FarmMemberDO m = s.getMemberId() == null ? null : members.get(s.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setStewardName(s.getStewardId() == null ? "" : users.getOrDefault(s.getStewardId(), ""));
            out.add(v);
        }
        return out;
    }
}
