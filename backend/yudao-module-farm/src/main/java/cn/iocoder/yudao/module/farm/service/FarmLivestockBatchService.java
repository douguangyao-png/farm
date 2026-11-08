package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo.FarmLivestockBatchOutReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo.FarmLivestockBatchPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.livestockbatch.vo.FarmLivestockBatchSaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmLivestockBatchDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmLivestockBatchMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 畜禽批次（第一期补齐；mock：routes/farm-p1b.ts 的 /livestock-batch/**）。0 在栏 / 10 出栏中 / 20 已出栏 */
@Service
public class FarmLivestockBatchService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private FarmLivestockBatchMapper mapper;
    @Autowired
    private FarmViewLookup lookup;
    @Autowired
    private FarmNoGenerator noGenerator;

    public PageResult<Map<String, Object>> page(FarmLivestockBatchPageReqVO req) {
        PageResult<FarmLivestockBatchDO> p = mapper.selectPage(req, new LambdaQueryWrapperX<FarmLivestockBatchDO>()
                .likeIfPresent(FarmLivestockBatchDO::getSpecies, req.getSpecies())
                .eqIfPresent(FarmLivestockBatchDO::getStatus, req.getStatus())
                .eqIfPresent(FarmLivestockBatchDO::getBaseId, req.getBaseId())
                .orderByDesc(FarmLivestockBatchDO::getCreateTime).orderByDesc(FarmLivestockBatchDO::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (FarmLivestockBatchDO b : p.getList()) list.add(view(b));
        return new PageResult<>(list, p.getTotal());
    }

    public Map<String, Object> get(Long id) {
        return view(must(id));
    }

    public Long create(FarmLivestockBatchSaveReqVO req) {
        FarmLivestockBatchDO b = new FarmLivestockBatchDO();
        fill(b, req, 0);
        b.setNo(noGenerator.next("XB"));
        b.setOutCount(0);
        b.setStatus(0);
        mapper.insert(b);
        return b.getId();
    }

    public void update(FarmLivestockBatchSaveReqVO req) {
        FarmLivestockBatchDO b = must(req.getId());
        int out = b.getOutCount() == null ? 0 : b.getOutCount();
        fill(b, req, out);
        // 数量调整后重新判断状态（满额 -> 已出栏）
        if (out > 0) b.setStatus(out >= b.getCount() ? 20 : 10);
        mapper.updateById(b);
    }

    /** 出栏：out_count 累加，不超过 count；满额置 20，否则 10。条件更新防并发超卖 */
    @Transactional(rollbackFor = Exception.class)
    public void out(FarmLivestockBatchOutReqVO req) {
        FarmLivestockBatchDO b = must(req.getId());
        Integer n = req.getCount();
        if (n == null || n <= 0) throw exception(BAD_REQUEST, "出栏数量要是大于 0 的整数");
        int out = b.getOutCount() == null ? 0 : b.getOutCount();
        if (out + n > b.getCount()) throw exception(BAD_REQUEST, "出栏数量超过在栏数量（剩余 " + (b.getCount() - out) + "）");
        int rows = mapper.update(null, new LambdaUpdateWrapper<FarmLivestockBatchDO>()
                .eq(FarmLivestockBatchDO::getId, b.getId())
                .setSql("out_count = out_count + " + n)
                .setSql("status = CASE WHEN out_count + " + n + " >= count THEN 20 ELSE 10 END")
                .apply("out_count + " + n + " <= count"));
        if (rows == 0) throw exception(BAD_REQUEST, "出栏数量超过在栏数量");
    }

    /** 仅 out_count=0 的批次可删 */
    public void delete(Long id) {
        FarmLivestockBatchDO b = must(id);
        if (b.getOutCount() != null && b.getOutCount() > 0) throw exception(BAD_REQUEST, "已有出栏记录的批次不能删除");
        mapper.deleteById(b.getId());
    }

    private FarmLivestockBatchDO must(Long id) {
        FarmLivestockBatchDO b = id == null ? null : mapper.selectById(id);
        if (b == null) throw exception(NOT_FOUND, "畜禽批次");
        return b;
    }

    /** 校验并写入可编辑字段（与 mock readFields 一致） */
    private void fill(FarmLivestockBatchDO b, FarmLivestockBatchSaveReqVO req, int outCount) {
        FarmBaseDO base = req.getBaseId() == null ? null : lookup.bases(List.of(req.getBaseId())).get(req.getBaseId());
        if (base == null) throw exception(BAD_REQUEST, "请选择基地");
        String species = req.getSpecies() == null ? "" : req.getSpecies().trim();
        if (species.isEmpty()) throw exception(BAD_REQUEST, "请填写畜禽种类");
        if (species.length() > 32) throw exception(BAD_REQUEST, "畜禽种类不能超过 32 个字");
        Integer count = req.getCount();
        if (count == null || count <= 0) throw exception(BAD_REQUEST, "数量要是大于 0 的整数");
        if (count < outCount) throw exception(BAD_REQUEST, "数量不能小于已出栏数 " + outCount);
        String breed = req.getBreed() == null ? "" : req.getBreed().trim();
        String pen = req.getPen() == null ? "" : req.getPen().trim();
        String remark = req.getRemark() == null ? "" : req.getRemark().trim();
        if (breed.length() > 64 || pen.length() > 64) throw exception(BAD_REQUEST, "品种、栏舍不能超过 64 个字");
        if (remark.length() > 255) throw exception(BAD_REQUEST, "备注不能超过 255 个字");
        long inMs = req.getInDate() == null ? System.currentTimeMillis() : req.getInDate();
        if (inMs <= 0) throw exception(BAD_REQUEST, "入栏日期不正确");
        b.setBaseId(base.getId());
        b.setSpecies(species);
        b.setBreed(breed);
        b.setInDate(Instant.ofEpochMilli(inMs).atZone(ZONE).toLocalDate());
        b.setCount(count);
        b.setPen(pen);
        b.setRemark(remark);
    }

    private Map<String, Object> view(FarmLivestockBatchDO b) {
        FarmBaseDO base = b.getBaseId() == null ? null : lookup.bases(List.of(b.getBaseId())).get(b.getBaseId());
        int out = b.getOutCount() == null ? 0 : b.getOutCount();
        LocalDate in = b.getInDate();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("no", b.getNo());
        m.put("baseId", b.getBaseId());
        m.put("baseName", base == null || base.getName() == null ? "" : base.getName());
        m.put("species", b.getSpecies());
        m.put("breed", StrUtil.nullToEmpty(b.getBreed()));
        m.put("inDate", in == null ? null : in.atStartOfDay(ZONE).toInstant().toEpochMilli());
        m.put("count", b.getCount());
        m.put("outCount", out);
        m.put("remaining", (b.getCount() == null ? 0 : b.getCount()) - out);
        m.put("status", b.getStatus());
        m.put("pen", StrUtil.nullToEmpty(b.getPen()));
        m.put("remark", StrUtil.nullToEmpty(b.getRemark()));
        m.put("createTime", FarmAppSupport.toMillis(b.getCreateTime()));
        return m;
    }
}
