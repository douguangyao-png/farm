package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage;
import cn.iocoder.yudao.module.farm.controller.admin.msgtemplate.vo.FarmMsgTemplateSaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMsgTemplateDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMsgTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.controller.admin.fulfill.FarmFulfillPage.m;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 消息模板（第二期 B；mock：routes/farm-ext-b.ts 的 /msg-template/**）。测试发送不真实下发，只返回代入示例变量后的预览。 */
@Service
public class FarmMsgTemplateService {

    private static final List<String> CHANNELS = Arrays.asList("订阅消息", "短信", "App 推送");

    @Autowired
    private FarmMsgTemplateMapper mapper;

    private static long ms(java.time.LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static Map<String, Object> view(FarmMsgTemplateDO x) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", x.getId());
        r.put("name", x.getName());
        r.put("trigger", x.getTriggerText());
        r.put("channels", x.getChannels());
        r.put("content", x.getContent());
        r.put("status", x.getStatus());
        r.put("createTime", ms(x.getCreateTime()));
        r.put("updateTime", ms(x.getUpdateTime()));
        return r;
    }

    public FarmFulfillPage<Map<String, Object>> page(PageParam req, String name, Integer status) {
        List<FarmMsgTemplateDO> all = mapper.selectList(new LambdaQueryWrapperX<FarmMsgTemplateDO>()
                .likeIfPresent(FarmMsgTemplateDO::getName, name).eqIfPresent(FarmMsgTemplateDO::getStatus, status).orderByAsc(FarmMsgTemplateDO::getId));
        return new FarmFulfillPage<>(all.stream().map(FarmMsgTemplateService::view).collect(Collectors.toList()), req, new LinkedHashMap<>());
    }

    /** 与 mock checkTemplate + 违禁词检测一致；命中违禁词抛 {@link FarmBannedWordsException}，由 Controller 转成带 data 的返回 */
    private void check(FarmMsgTemplateSaveReqVO b) {
        if (StrUtil.isBlank(b.getName())) throw exception(BAD_REQUEST, "请填写模板名称");
        if (StrUtil.isBlank(b.getTrigger())) throw exception(BAD_REQUEST, "请填写触发时机");
        if (StrUtil.isBlank(b.getContent())) throw exception(BAD_REQUEST, "请填写模板内容");
        if (b.getChannels() == null || b.getChannels().isEmpty() || !CHANNELS.containsAll(b.getChannels())) throw exception(BAD_REQUEST, "请选择发送渠道");
        List<String> words = FarmComplianceService.findBannedWords(Arrays.asList(b.getName(), b.getContent()));
        if (!words.isEmpty()) throw new FarmBannedWordsException(words);
    }

    public Long create(FarmMsgTemplateSaveReqVO req) {
        check(req);
        FarmMsgTemplateDO x = new FarmMsgTemplateDO();
        x.setName(req.getName().trim());
        x.setTriggerText(req.getTrigger().trim());
        x.setChannels(new ArrayList<>(req.getChannels()));
        x.setContent(req.getContent().trim());
        x.setStatus(0);
        mapper.insert(x);
        return x.getId();
    }

    private FarmMsgTemplateDO get(Long id) {
        FarmMsgTemplateDO x = id == null ? null : mapper.selectById(id);
        if (x == null) throw exception(NOT_FOUND, "消息模板");
        return x;
    }

    public void update(FarmMsgTemplateSaveReqVO req) {
        FarmMsgTemplateDO x = get(req.getId());
        // 请求里没传的字段沿用原值，再整体校验
        if (req.getName() == null) req.setName(x.getName());
        if (req.getTrigger() == null) req.setTrigger(x.getTriggerText());
        if (req.getChannels() == null) req.setChannels(x.getChannels());
        if (req.getContent() == null) req.setContent(x.getContent());
        check(req);
        x.setName(req.getName().trim());
        x.setTriggerText(req.getTrigger().trim());
        x.setChannels(new ArrayList<>(req.getChannels()));
        x.setContent(req.getContent().trim());
        mapper.updateById(x);
    }

    public void updateStatus(Long id, Integer status) {
        FarmMsgTemplateDO x = get(id);
        x.setStatus(status != null && status == 1 ? 1 : 0);
        mapper.updateById(x);
    }

    public Map<String, Object> testSend(Long id) {
        FarmMsgTemplateDO x = get(id);
        if (x.getStatus() != null && x.getStatus() != 0) throw exception(BAD_REQUEST, "模板已停用，请先启用");
        return m("sent", false, "demo", true, "channels", x.getChannels(), "preview", x.getContent().replaceAll("\\{[^}]+\\}", "示例"));
    }
}
