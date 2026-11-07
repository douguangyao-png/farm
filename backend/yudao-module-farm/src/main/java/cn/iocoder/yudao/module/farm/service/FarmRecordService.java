package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordAuditReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmRecordDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmRecordMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 接口契约 2.6 农事记录审核（mock：farm-admin.ts 264–285 行） */
@Service
public class FarmRecordService {

    /** 契约 1_030_000_006：状态已变化（提示语由调用处传入，与 mock 一致） */
    private static final ErrorCode RECORD_STATE = new ErrorCode(1_030_000_006, "{}");

    @Autowired
    private FarmRecordMapper recordMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmViewLookup lookup;

    public PageResult<FarmRecordRespVO> page(FarmRecordPageReqVO req) {
        LambdaQueryWrapperX<FarmRecordDO> w = new LambdaQueryWrapperX<FarmRecordDO>()
                .eqIfPresent(FarmRecordDO::getStatus, req.getStatus())
                .eqIfPresent(FarmRecordDO::getStewardId, req.getStewardId());
        if (StrUtil.isNotEmpty(req.getAssetCode())) {
            List<Long> assetIds = assetMapper.selectList(new LambdaQueryWrapperX<FarmAssetDO>()
                            .like(FarmAssetDO::getCode, req.getAssetCode()))
                    .stream().map(FarmAssetDO::getId).collect(Collectors.toList());
            if (assetIds.isEmpty()) return PageResult.empty();
            w.in(FarmRecordDO::getAssetId, assetIds);
        }
        w.orderByDesc(FarmRecordDO::getCreateTime).orderByDesc(FarmRecordDO::getId);
        PageResult<FarmRecordDO> p = recordMapper.selectPage(req, w);
        return new PageResult<>(toViews(p.getList()), p.getTotal());
    }

    public FarmRecordRespVO get(Long id) {
        FarmRecordDO r = id == null ? null : recordMapper.selectById(id);
        if (r == null) throw exception(NOT_FOUND, "农事记录");
        return toViews(List.of(r)).get(0);
    }

    /** 审核：通过 status=10，驳回 status=20（驳回必填原因）；只有 status=0 能审。mock 里审核没有别的联动。 */
    public void audit(FarmRecordAuditReqVO req) {
        FarmRecordDO r = req.getId() == null ? null : recordMapper.selectById(req.getId());
        if (r == null) throw exception(NOT_FOUND, "农事记录");
        boolean pass = Boolean.TRUE.equals(req.getPass());
        if (!pass && StrUtil.isBlank(req.getRemark())) throw exception(BAD_REQUEST, "驳回时请填写原因");
        if (r.getStatus() == null || r.getStatus() != 0) throw exception(RECORD_STATE, "这条记录已经审核过了");
        FarmRecordDO u = new FarmRecordDO();
        u.setStatus(pass ? 10 : 20);
        u.setAuditRemark(req.getRemark());
        u.setAuditTime(LocalDateTime.now());
        // 带上 status=0 条件，防止并发重复审核
        int rows = recordMapper.update(u, new LambdaUpdateWrapper<FarmRecordDO>()
                .eq(FarmRecordDO::getId, r.getId()).eq(FarmRecordDO::getStatus, 0));
        if (rows == 0) throw exception(RECORD_STATE, "这条记录已经审核过了");
    }

    /** 批量拼装 recordView（assetCode / memberName / stewardName），供订单详情等复用 */
    public List<FarmRecordRespVO> toViews(List<FarmRecordDO> list) {
        Map<Long, FarmAdoptionDO> adoptions = lookup.adoptions(list.stream().map(FarmRecordDO::getAdoptionId).collect(Collectors.toList()));
        Map<Long, FarmAssetDO> assets = lookup.assets(list.stream().map(FarmRecordDO::getAssetId).collect(Collectors.toList()));
        Map<Long, FarmMemberDO> members = lookup.members(adoptions.values().stream().map(FarmAdoptionDO::getMemberId).collect(Collectors.toList()));
        Map<Long, String> users = lookup.userNames(list.stream().map(FarmRecordDO::getStewardId).collect(Collectors.toList()));
        List<FarmRecordRespVO> out = new ArrayList<>();
        for (FarmRecordDO r : list) {
            FarmRecordRespVO v = BeanUtils.toBean(r, FarmRecordRespVO.class);
            FarmAssetDO a = r.getAssetId() == null ? null : assets.get(r.getAssetId());
            v.setAssetCode(a == null ? "" : a.getCode());
            FarmAdoptionDO ad = r.getAdoptionId() == null ? null : adoptions.get(r.getAdoptionId());
            FarmMemberDO m = ad == null ? null : members.get(ad.getMemberId());
            v.setMemberName(m == null || m.getNickname() == null ? "" : m.getNickname());
            v.setStewardName(r.getStewardId() == null ? "" : users.getOrDefault(r.getStewardId(), ""));
            out.add(v);
        }
        return out;
    }
}
