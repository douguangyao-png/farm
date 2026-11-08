package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.contract.vo.FarmContractPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.contract.vo.FarmContractRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 合同管理台账（mock：routes/farm-contract.ts）。由订单派生，只读。 */
@Service
public class FarmContractService {

    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmViewLookup lookup;

    public PageResult<FarmContractRespVO> page(FarmContractPageReqVO req) {
        LambdaQueryWrapperX<FarmOrderDO> w = new LambdaQueryWrapperX<>();
        if (StrUtil.isNotEmpty(req.getNo())) {
            // 合同编号 = HT + 订单号去掉前两位；按编号查时去掉 HT 前缀，匹配订单号
            w.like(FarmOrderDO::getNo, req.getNo().replaceFirst("^(?i)HT", ""));
        }
        if (req.getStatus() != null) {
            if (req.getStatus() == 20) {
                w.in(FarmOrderDO::getStatus, 30, 50);
            } else if (req.getStatus() == 10) {
                w.notIn(FarmOrderDO::getStatus, 30, 50).eq(FarmOrderDO::getContractAgreed, true).isNotNull(FarmOrderDO::getPayTime);
            } else {
                w.notIn(FarmOrderDO::getStatus, 30, 50)
                        .and(x -> x.eq(FarmOrderDO::getContractAgreed, false)
                                .or().isNull(FarmOrderDO::getContractAgreed)
                                .or().isNull(FarmOrderDO::getPayTime));
            }
        }
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

    private List<FarmContractRespVO> toViews(List<FarmOrderDO> list) {
        Map<Long, FarmMemberDO> members = lookup.members(list.stream().map(FarmOrderDO::getMemberId).collect(Collectors.toList()));
        Map<Long, FarmProductDO> products = lookup.products(list.stream().map(FarmOrderDO::getProductId).collect(Collectors.toList()));
        List<FarmContractRespVO> out = new ArrayList<>();
        for (FarmOrderDO o : list) {
            FarmContractRespVO v = new FarmContractRespVO();
            FarmMemberDO m = o.getMemberId() == null ? null : members.get(o.getMemberId());
            FarmProductDO pr = o.getProductId() == null ? null : products.get(o.getProductId());
            String no = o.getNo() == null ? "" : o.getNo();
            int status = statusOf(o);
            v.setId(o.getId());
            v.setNo("HT" + (no.length() > 2 ? no.substring(2) : ""));
            v.setOrderId(o.getId());
            v.setOrderNo(no);
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setMemberMobile(m == null || m.getMobile() == null ? "" : m.getMobile());
            v.setProductTitle(pr == null || pr.getTitle() == null ? "" : pr.getTitle());
            v.setCareMode(o.getCareMode());
            v.setCareModeName(FarmViewLookup.careModeName(o.getCareMode()));
            v.setVersion(o.getContractVersion());
            v.setTitle("《认养服务合同（" + FarmViewLookup.careModeName(o.getCareMode()) + "）》" + o.getContractVersion());
            v.setStatus(status);
            v.setSignMethod("电子签名");
            v.setCaAuth(status == 10);
            v.setChainProof(status == 10);
            v.setSignedAt(o.getSignedAt() != null ? o.getSignedAt() : (status == 10 ? o.getPayTime() : null));
            v.setHasSignature(StrUtil.isNotEmpty(o.getSignature()));
            v.setCreateTime(o.getCreateTime());
            out.add(v);
        }
        return out;
    }

    /** 与 mock contractStatusOf 一致 */
    private static int statusOf(FarmOrderDO o) {
        if (o.getStatus() != null && (o.getStatus() == 30 || o.getStatus() == 50)) return 20;
        return Boolean.TRUE.equals(o.getContractAgreed()) && o.getPayTime() != null ? 10 : 0;
    }
}
