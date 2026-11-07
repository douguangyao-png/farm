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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** 统计卡（对应 mock record/stats）：本月自动过检 / 需人工复核按本月提交的记录算 */
    public Map<String, Object> stats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        long todayCount = recordMapper.selectCount(new LambdaQueryWrapperX<FarmRecordDO>().ge(FarmRecordDO::getCreateTime, startToday));
        long pendingCount = recordMapper.selectCount(FarmRecordDO::getStatus, 0);
        List<FarmRecordDO> month = recordMapper.selectList(new LambdaQueryWrapperX<FarmRecordDO>().ge(FarmRecordDO::getCreateTime, monthStart));
        long autoPass = month.stream().filter(r -> passed(r, "exifTime") && passed(r, "gpsFence") && passed(r, "duplicate")).count();
        long assetCount = month.stream().map(FarmRecordDO::getAssetId).filter(java.util.Objects::nonNull).distinct().count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("todayCount", todayCount);
        out.put("pendingCount", pendingCount);
        out.put("autoPassCount", autoPass);
        out.put("manualReviewCount", month.size() - autoPass);
        out.put("monthCount", month.size());
        out.put("avgPerAsset", assetCount == 0 ? 0 : Math.round(month.size() * 10.0 / assetCount) / 10.0);
        return out;
    }

    private static boolean passed(FarmRecordDO r, String key) {
        return r.getChecks() != null && Boolean.TRUE.equals(r.getChecks().get(key));
    }

    private static String kindName(String kind) {
        if ("farming".equals(kind)) return "农事";
        if ("harvest".equals(kind)) return "采收";
        if ("service".equals(kind)) return "代管服务";
        return "";
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
            String kindName = kindName(r.getKind());
            v.setKindName(kindName);
            v.setSummary(kindName + " · " + r.getTitle());
            v.setPhotoCount(r.getPhotos() == null ? 0 : r.getPhotos().size());
            List<String> notes = new ArrayList<>();
            if (!passed(r, "exifTime")) notes.add("拍摄时间偏差");
            if (!passed(r, "gpsFence")) notes.add("地点偏移");
            if (!passed(r, "duplicate")) notes.add("画面重复率高");
            v.setMachineCheck(notes.isEmpty() ? "ok" : "suspect");
            v.setMachineCheckNote(String.join("、", notes));
            out.add(v);
        }
        return out;
    }
}
