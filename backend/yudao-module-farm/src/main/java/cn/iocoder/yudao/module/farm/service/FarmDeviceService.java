package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.device.vo.FarmDeviceSaveReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmDeviceDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmDeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.m;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.r1;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * IoT 设备、视频监控（第二期 B；mock：farm/ext-b.ts 的 devicesOf / deviceStats / cameraView）。
 * 设备清单 = 资产上的摄像头/传感器名称去重 + 手动添加的设备（farm_device.custom=1）；
 * farm_device.custom=0 的行是对派生设备的覆盖（改位置/固件、派单）。状态、最近上报、固件没有真实接入，用确定性的演示值。
 */
@Service
public class FarmDeviceService {

    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private FarmDeviceMapper deviceMapper;

    /** 一台设备 */
    public static class Device {
        public String code, kind, typeName, name, position, bindText, status, statusName, firmware;
        public List<String> assetCodes = new ArrayList<>();
        public long lastReportTime;
        public boolean dispatched, custom;

        public Map<String, Object> toMap() {
            return m("code", code, "kind", kind, "typeName", typeName, "name", name, "position", position, "bindText", bindText,
                    "assetCodes", assetCodes, "status", status, "statusName", statusName, "lastReportTime", lastReportTime,
                    "firmware", firmware, "dispatched", dispatched, "custom", custom);
        }
    }

    private static String bindText(List<String> codes) {
        if (codes.size() <= 3) return String.join("、", codes);
        return codes.get(0) + " ~ " + codes.get(codes.size() - 1) + "（" + codes.size() + " 块）";
    }

    public List<Device> devices() {
        List<FarmAssetDO> assets = assetMapper.selectList(new LambdaQueryWrapperX<FarmAssetDO>().orderByAsc(FarmAssetDO::getId));
        List<FarmDeviceDO> rows = deviceMapper.selectList(new LambdaQueryWrapperX<FarmDeviceDO>().orderByAsc(FarmDeviceDO::getId));
        Map<String, FarmDeviceDO> override = new LinkedHashMap<>();
        List<FarmDeviceDO> customs = new ArrayList<>();
        for (FarmDeviceDO r : rows) {
            if (r.getCustom() != null && r.getCustom() == 1) customs.add(r);
            else override.put(r.getCode(), r);
        }
        Map<String, List<String>> cams = new LinkedHashMap<>();
        Map<String, List<String>> sens = new LinkedHashMap<>();
        for (FarmAssetDO a : assets) {
            if (StrUtil.isNotEmpty(a.getCameraName())) cams.computeIfAbsent(a.getCameraName(), k -> new ArrayList<>()).add(a.getCode());
            if (StrUtil.isNotEmpty(a.getSensorName())) sens.computeIfAbsent(a.getSensorName(), k -> new ArrayList<>()).add(a.getCode());
        }
        long now = System.currentTimeMillis();
        List<Device> out = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, List<String>> e : cams.entrySet()) {
            out.add(build("camera", "摄像头", String.format("CAM-%02d", i + 1), e.getKey(), e.getValue(), "v3.2.1",
                    cams.size() >= 3 && i == cams.size() - 1, i, null, override, now));
            i++;
        }
        i = 0;
        for (Map.Entry<String, List<String>> e : sens.entrySet()) {
            boolean weather = e.getKey().contains("气象");
            out.add(build("sensor", weather ? "气象站" : "土壤墒情站", String.format("SEN-%02d", i + 1), e.getKey(), e.getValue(),
                    weather ? "v2.4.0" : "v1.8.0", false, i + 5, null, override, now));
            i++;
        }
        i = 0;
        for (FarmDeviceDO c : customs) {
            out.add(build(c.getKind(), c.getTypeName(), c.getCode(), c.getName(), new ArrayList<>(),
                    StrUtil.isEmpty(c.getFirmware()) ? "v1.0.0" : c.getFirmware(), false, i + 9, c, override, now));
            i++;
        }
        return out;
    }

    private Device build(String kind, String typeName, String code, String name, List<String> codes, String firmware, boolean offline,
                         int idx, FarmDeviceDO custom, Map<String, FarmDeviceDO> override, long now) {
        FarmDeviceDO ov = override.get(code);
        Device d = new Device();
        d.code = code;
        d.kind = kind;
        d.typeName = typeName;
        d.name = name;
        d.position = ov != null && StrUtil.isNotBlank(ov.getPosition()) ? ov.getPosition()
                : custom != null && StrUtil.isNotBlank(custom.getPosition()) ? custom.getPosition() : name;
        d.bindText = custom != null ? custom.getBindText() : bindText(codes);
        d.assetCodes = codes;
        d.status = offline ? "offline" : "online";
        d.statusName = offline ? "离线" : "在线";
        d.lastReportTime = now - (offline ? 2 * 3600_000L : ((idx * 3 + 2) % 9 + 1) * 60_000L);
        d.firmware = ov != null && StrUtil.isNotBlank(ov.getFirmware()) ? ov.getFirmware() : firmware;
        d.dispatched = ov != null && ov.getDispatched() != null && ov.getDispatched() == 1;
        d.custom = custom != null;
        return d;
    }

    public Map<String, Object> stats(List<Device> rows) {
        long cams = rows.stream().filter(r -> "camera".equals(r.kind)).count();
        long online = rows.stream().filter(r -> "online".equals(r.status)).count();
        return m("total", rows.size(), "cameraCount", cams, "sensorCount", rows.size() - cams, "onlineCount", online,
                "offlineCount", rows.size() - online, "todayReports", online * 288, "retainDays", 30, "storageUsedGb", 42, "storageTotalGb", 100);
    }

    public FarmFulfillPage<Map<String, Object>> page(PageParam req, String code, String kind, String status) {
        List<Device> all = devices();
        List<Map<String, Object>> rows = all.stream()
                .filter(r -> StrUtil.isEmpty(code) || r.code.contains(code))
                .filter(r -> StrUtil.isEmpty(kind) || r.kind.equals(kind))
                .filter(r -> StrUtil.isEmpty(status) || r.status.equals(status))
                .map(Device::toMap).collect(Collectors.toList());
        return new FarmFulfillPage<>(rows, req, stats(all));
    }

    public String create(FarmDeviceSaveReqVO req) {
        if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "请填写设备名称");
        String kind = "sensor".equals(req.getKind()) ? "sensor" : "camera";
        String typeName = "camera".equals(kind) ? "摄像头"
                : Arrays.asList("土壤墒情站", "气象站", "RFID 耳标/脚环").contains(req.getTypeName()) ? req.getTypeName() : "土壤墒情站";
        long customCount = deviceMapper.selectList(new LambdaQueryWrapperX<FarmDeviceDO>().eq(FarmDeviceDO::getCustom, 1)).size();
        FarmDeviceDO d = new FarmDeviceDO();
        d.setCode((("camera".equals(kind)) ? "CAM" : "SEN") + "-X" + String.format("%02d", customCount + 1));
        d.setCustom(1);
        d.setKind(kind);
        d.setTypeName(typeName);
        d.setName(req.getName().trim());
        d.setPosition(StrUtil.isBlank(req.getPosition()) ? req.getName().trim() : req.getPosition().trim());
        d.setBindText(StrUtil.isBlank(req.getBindText()) ? "未绑定" : req.getBindText().trim());
        d.setFirmware("v1.0.0");
        d.setDispatched(0);
        deviceMapper.insert(d);
        return d.getCode();
    }

    private FarmDeviceDO overrideRow(String code) {
        if (devices().stream().noneMatch(x -> x.code.equals(code))) throw exception(NOT_FOUND, "设备");
        FarmDeviceDO row = deviceMapper.selectOne(new LambdaQueryWrapperX<FarmDeviceDO>().eq(FarmDeviceDO::getCode, code).eq(FarmDeviceDO::getCustom, 0));
        if (row == null) {
            row = new FarmDeviceDO();
            row.setCode(code);
            row.setCustom(0);
            row.setDispatched(0);
            row.setKind("");
            row.setTypeName("");
            row.setName("");
            deviceMapper.insert(row);
        }
        return row;
    }

    public void update(FarmDeviceSaveReqVO req) {
        FarmDeviceDO row = overrideRow(req.getCode());
        if (StrUtil.isNotBlank(req.getPosition())) row.setPosition(req.getPosition().trim());
        if (StrUtil.isNotBlank(req.getFirmware())) row.setFirmware(req.getFirmware().trim());
        deviceMapper.updateById(row);
    }

    public void dispatch(String code) {
        Device d = devices().stream().filter(x -> x.code.equals(code)).findFirst().orElseThrow(() -> exception(NOT_FOUND, "设备"));
        if (!"offline".equals(d.status)) throw exception(BAD_REQUEST, "只有离线设备可以派单");
        FarmDeviceDO row = overrideRow(code);
        row.setDispatched(1);
        deviceMapper.updateById(row);
    }

    /** 视频监控页：摄像头 + 统计 + 开放策略（说明文案） */
    public Map<String, Object> cameraOverview() {
        List<Device> cams = devices().stream().filter(x -> "camera".equals(x.kind)).collect(Collectors.toList());
        List<FarmAssetDO> assets = assetMapper.selectList();
        List<FarmAdoptionDO> active = adoptionMapper.selectList(new LambdaQueryWrapperX<FarmAdoptionDO>().eq(FarmAdoptionDO::getStatus, 10));
        List<Map<String, Object>> list = new ArrayList<>();
        int views = 0;
        long online = 0;
        for (int i = 0; i < cams.size(); i++) {
            Device c = cams.get(i);
            Set<Long> assetIds = assets.stream().filter(a -> c.assetCodes.contains(a.getCode())).map(FarmAssetDO::getId).collect(Collectors.toSet());
            Set<Long> members = new HashSet<>();
            for (FarmAdoptionDO a : active) if (assetIds.contains(a.getAssetId())) members.add(a.getMemberId());
            boolean off = "offline".equals(c.status);
            int todayViews = off ? 0 : 12 + ((i * 37) % 41);
            views += todayViews;
            if (!off) online++;
            Map<String, Object> row = c.toMap();
            row.put("visibleMembers", off ? 0 : members.size());
            row.put("todayViews", todayViews);
            list.add(row);
        }
        List<Map<String, Object>> policy = new ArrayList<>();
        String[][] kv = {{"可见范围", "仅该标的认养人可见"}, {"游客模式", "可开启 15 秒试看（用于拉新）"}, {"水印", "画面叠加时间戳 + 地块编号（防录播造假）"},
                {"回放权限", "认养人可调阅近 30 天录像片段"}, {"离线策略", "展示最后在线快照 + 在线状态提示"}, {"画面静止检测", "已开启 · 6h 无变化触发复核"}};
        for (String[] x : kv) policy.add(m("k", x[0], "v", x[1]));
        return m("demo", true,
                "stats", m("cameraCount", cams.size(), "onlineCount", online, "offlineCount", cams.size() - online, "todayViews", views,
                        "watchHours", r1(views * 3.1 / 60), "retainDays", 30, "alertCount", cams.size() - online),
                "cameras", list, "policy", policy);
    }
}
