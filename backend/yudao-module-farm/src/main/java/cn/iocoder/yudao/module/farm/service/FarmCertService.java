package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.cert.vo.FarmCertSaveReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmCertDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmCertMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.m;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 资质证照（第二期 B；mock：farm/ext-b.ts 的 certs / certRecords，routes/farm-ext-b.ts 的 /cert/**）。
 * 表 farm_cert 存预置演示证照（demo=1）和经营者上传的证照；基地资料里的备案编号（farm_base.cert_no）每个基地派生一行，只读。
 */
@Service
public class FarmCertService {

    private static final long DAY = 86400_000L;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final List<String> TYPES = Arrays.asList("必备", "建议", "按需");
    private static final List<String> REQUIRED = Arrays.asList("营业执照", "土地流转合同", "政策性农业保险保单");

    @Autowired
    private FarmCertMapper certMapper;
    @Autowired
    private FarmBaseMapper baseMapper;

    private static long ms(LocalDateTime t) {
        return t.atZone(ZONE).toInstant().toEpochMilli();
    }

    private static long at(String ymd) {
        return LocalDate.parse(ymd).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    private Map<String, Object> view(Long id, String name, String no, String type, Long validUntil, String verify, String publicMode, String fileUrl,
                                     String source, boolean demo, long createTime) {
        long now = System.currentTimeMillis();
        boolean expired = validUntil != null && validUntil < now;
        Long daysLeft = validUntil == null ? null : (long) Math.ceil((validUntil - now) / (double) DAY);
        String v = expired ? "expired" : verify;
        String vName = "verified".equals(v) ? "已核验" : "pending".equals(v) ? "待核验" : "renew".equals(v) ? "待更新" : "已过期";
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("name", name);
        r.put("no", no);
        r.put("type", type);
        r.put("validUntil", validUntil);
        r.put("verify", v);
        r.put("verifyName", vName);
        r.put("publicMode", publicMode);
        r.put("publicName", "public".equals(publicMode) ? "公示" : "不公示（可预约查阅）");
        r.put("fileUrl", fileUrl == null ? "" : fileUrl);
        r.put("source", source);
        r.put("demo", demo);
        r.put("createTime", createTime);
        r.put("daysLeft", daysLeft);
        r.put("expiringSoon", !expired && daysLeft != null && daysLeft <= 30);
        r.put("editable", "upload".equals(source));
        return r;
    }

    private Map<String, Object> toView(FarmCertDO c) {
        return view(c.getId(), c.getName(), c.getNo(), c.getCertType(), c.getValidUntil() == null ? null : ms(c.getValidUntil()), c.getVerify(),
                c.getPublicMode(), c.getFileUrl(), "upload", c.getDemo() != null && c.getDemo() == 1, ms(c.getCreateTime()));
    }

    /** 全部证照：预置演示 → 基地备案 → 经营者上传 */
    public List<Map<String, Object>> all() {
        List<FarmCertDO> rows = certMapper.selectList(new LambdaQueryWrapperX<FarmCertDO>().orderByAsc(FarmCertDO::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (FarmCertDO c : rows) if (c.getDemo() != null && c.getDemo() == 1) out.add(toView(c));
        for (FarmBaseDO b : baseMapper.selectList(new LambdaQueryWrapperX<FarmBaseDO>().orderByAsc(FarmBaseDO::getId))) {
            if (StrUtil.isEmpty(b.getCertNo())) continue;
            out.add(view(9000L + b.getId(), "基地备案证（" + b.getName() + "）", b.getCertNo(), "必备", null, "verified", "public", "", "base", false, ms(b.getCreateTime())));
        }
        for (FarmCertDO c : rows) if (c.getDemo() == null || c.getDemo() != 1) out.add(toView(c));
        return out;
    }

    /** 核验记录：3 条演示 + 每份经营者上传的证照一条 */
    public List<Map<String, Object>> records() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(m("text", "营业执照通过人工复核", "done", true, "by", "平台风控", "time", at("2026-03-02")));
        out.add(m("text", "保险保单与标的匹配成功", "done", true, "by", "系统", "time", at("2026-03-05")));
        out.add(m("text", "检测报告即将到期，已提醒上传新版", "done", false, "by", "系统", "time", now));
        for (Map<String, Object> c : all()) {
            if ("upload".equals(c.get("source")) && !Boolean.TRUE.equals(c.get("demo"))) {
                boolean pending = "pending".equals(c.get("verify"));
                out.add(m("text", "上传《" + c.get("name") + "》，" + (pending ? "等待平台人工复核" : "已核验"), "done", !pending, "by", "系统", "time", c.get("createTime")));
            }
        }
        out.sort(Comparator.comparingLong((Map<String, Object> x) -> (Long) x.get("time")).reversed());
        return out;
    }

    public FarmFulfillPage<Map<String, Object>> page(PageParam req, String name, String type) {
        List<Map<String, Object>> all = all();
        List<Map<String, Object>> rows = all.stream()
                .filter(c -> StrUtil.isEmpty(name) || String.valueOf(c.get("name")).contains(name))
                .filter(c -> StrUtil.isEmpty(type) || type.equals(c.get("type")))
                .collect(Collectors.toList());
        long missing = REQUIRED.stream().filter(n -> all.stream().noneMatch(c -> n.equals(c.get("name")))).count();
        Map<String, Object> stats = m("total", all.size(),
                "expiringSoon", all.stream().filter(c -> Boolean.TRUE.equals(c.get("expiringSoon"))).count(),
                "expired", all.stream().filter(c -> "expired".equals(c.get("verify"))).count(),
                "missingRequired", missing, "records", records());
        return new FarmFulfillPage<>(rows, req, stats);
    }

    public Long create(FarmCertSaveReqVO req) {
        if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "请填写证照名称");
        if (StrUtil.isBlank(req.getNo())) throw exception(BAD_REQUEST, "请填写证照编号");
        if (!TYPES.contains(req.getType())) throw exception(BAD_REQUEST, "请选择证照类型");
        FarmCertDO c = new FarmCertDO();
        c.setName(req.getName().trim());
        c.setNo(req.getNo().trim());
        c.setCertType(req.getType());
        c.setValidUntil(req.getValidUntil() == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(req.getValidUntil()), ZONE));
        c.setVerify("pending");
        c.setPublicMode("private".equals(req.getPublicMode()) ? "private" : "public");
        c.setFileUrl(req.getFileUrl() == null ? "" : req.getFileUrl());
        c.setDemo(0);
        certMapper.insert(c);
        return c.getId();
    }

    public void updatePublic(Long id, String publicMode) {
        if (id != null && id >= 9000) throw exception(BAD_REQUEST, "基地备案证来自基地资料，请到基地管理修改");
        FarmCertDO c = id == null ? null : certMapper.selectById(id);
        if (c == null) throw exception(NOT_FOUND, "证照");
        c.setPublicMode("private".equals(publicMode) ? "private" : "public");
        certMapper.updateById(c);
    }
}
