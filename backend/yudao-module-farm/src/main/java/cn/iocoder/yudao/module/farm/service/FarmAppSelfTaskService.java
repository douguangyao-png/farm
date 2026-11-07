package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.module.farm.controller.app.selftask.vo.FarmAppSelfTaskRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmServiceOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmTaskDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmServiceOrderMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.TASK_STATUS_CHANGED;

/** C 端自管任务：列表、标记完成、逾期委托代办（mock：farm-app.ts 196–236 行） */
@Service
public class FarmAppSelfTaskService {

    @Autowired
    private FarmTaskMapper taskMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmServiceOrderMapper serviceOrderMapper;
    @Autowired
    private FarmAppSupport support;
    @Autowired
    private FarmNoGenerator noGenerator;
    @Autowired
    private FarmViewLookup lookup;

    /** mock 197–209 行：我的自管任务；逾期的排前面，再按截止时间从早到晚 */
    public List<FarmAppSelfTaskRespVO> list(Long adoptionId, Integer status) {
        Long memberId = support.memberId();
        List<Long> adIds = adoptionMapper.selectList(new LambdaQueryWrapper<FarmAdoptionDO>()
                        .eq(FarmAdoptionDO::getMemberId, memberId))
                .stream().map(FarmAdoptionDO::getId).collect(Collectors.toList());
        List<FarmAppSelfTaskRespVO> out = new ArrayList<>();
        if (adIds.isEmpty()) return out;

        LambdaQueryWrapper<FarmTaskDO> w = new LambdaQueryWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getOwner, "member")
                .in(FarmTaskDO::getAdoptionId, adIds);
        if (adoptionId != null) w.eq(FarmTaskDO::getAdoptionId, adoptionId);
        if (status != null) w.eq(FarmTaskDO::getStatus, status);
        List<FarmTaskDO> tasks = taskMapper.selectList(w);

        Map<Long, FarmAssetDO> assets = lookup.assets(tasks.stream().map(FarmTaskDO::getAssetId).collect(Collectors.toList()));
        LocalDateTime now = LocalDateTime.now();
        for (FarmTaskDO t : tasks) {
            FarmAppSelfTaskRespVO v = new FarmAppSelfTaskRespVO();
            FarmAssetDO a = t.getAssetId() == null ? null : assets.get(t.getAssetId());
            v.setId(t.getId());
            v.setAdoptionId(t.getAdoptionId());
            v.setAssetCode(a == null || a.getCode() == null ? "" : a.getCode());
            v.setTitle(t.getTitle());
            v.setDescription(t.getDescription());
            v.setDeadline(t.getDeadline());
            v.setStatus(t.getStatus());
            v.setOverdue(t.getStatus() != null && t.getStatus() == 0 && t.getDeadline() != null && t.getDeadline().isBefore(now));
            v.setEntrustPrice(t.getEntrustPrice());
            v.setFinishTime(t.getFinishTime());
            v.setServiceOrderId(t.getServiceOrderId());
            out.add(v);
        }
        out.sort((x, y) -> {
            boolean ox = Boolean.TRUE.equals(x.getOverdue());
            boolean oy = Boolean.TRUE.equals(y.getOverdue());
            if (ox != oy) return ox ? -1 : 1;
            if (x.getDeadline() == null) return y.getDeadline() == null ? 0 : 1;
            if (y.getDeadline() == null) return -1;
            return x.getDeadline().compareTo(y.getDeadline());
        });
        return out;
    }

    /** mock 216–223 行：标记完成。只有待办能完成；条件更新防并发 */
    public boolean done(Long id) {
        FarmTaskDO t = mine(id);
        if (t.getStatus() == null || t.getStatus() != 0) throw exception(TASK_STATUS_CHANGED);
        int rows = taskMapper.update(null, new LambdaUpdateWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getId, t.getId())
                .eq(FarmTaskDO::getStatus, 0)
                .set(FarmTaskDO::getStatus, 10)
                .set(FarmTaskDO::getFinishTime, LocalDateTime.now()));
        if (rows == 0) throw exception(TASK_STATUS_CHANGED);
        return true;
    }

    /** mock 224–236 行：委托管家代办 = 生成一张待接单的代管工单，任务置为已委托（R-5） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> entrust(Long id) {
        FarmTaskDO t = mine(id);
        if (t.getStatus() == null || t.getStatus() != 0) throw exception(TASK_STATUS_CHANGED);

        Map<String, Object> split = new LinkedHashMap<>();
        split.put("steward", 70);
        split.put("farm", 20);
        split.put("platform", 10);
        FarmServiceOrderDO so = new FarmServiceOrderDO();
        so.setNo(noGenerator.next("DG"));
        so.setFromTaskId(t.getId());
        so.setTaskTitle(t.getTitle());
        so.setAdoptionId(t.getAdoptionId());
        so.setAssetId(t.getAssetId());
        so.setMemberId(support.memberId());
        so.setStewardId(null);
        so.setServiceType("代" + t.getTitle());
        so.setPrice(t.getEntrustPrice() != null ? t.getEntrustPrice() : 3000L);
        so.setStatus(0);
        so.setSplit(split);
        so.setProofPhotos(new ArrayList<>());
        so.setRemark("认养人委托：" + (t.getDescription() == null ? "" : t.getDescription()));
        so.setAcceptTime(null);
        so.setFinishTime(null);
        serviceOrderMapper.insert(so);

        int rows = taskMapper.update(null, new LambdaUpdateWrapper<FarmTaskDO>()
                .eq(FarmTaskDO::getId, t.getId())
                .eq(FarmTaskDO::getStatus, 0)
                .set(FarmTaskDO::getStatus, 20)
                .set(FarmTaskDO::getServiceOrderId, so.getId()));
        if (rows == 0) throw exception(TASK_STATUS_CHANGED); // 事务回滚，工单一并撤销

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serviceOrderId", so.getId());
        out.put("no", so.getNo());
        out.put("price", so.getPrice());
        return out;
    }

    /** mock 210–215 行：只能处理自己认养下、认养人自管（owner=member）的任务；否则 404「任务不存在」 */
    private FarmTaskDO mine(Long id) {
        FarmTaskDO t = id == null ? null : taskMapper.selectById(id);
        if (t == null || !"member".equals(t.getOwner()) || t.getAdoptionId() == null) throw exception(NOT_FOUND, "任务");
        FarmAdoptionDO ad = adoptionMapper.selectById(t.getAdoptionId());
        if (ad == null || !support.memberId().equals(ad.getMemberId())) throw exception(NOT_FOUND, "任务");
        return t;
    }
}
