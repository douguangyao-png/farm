package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmComplianceHitDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmComplianceHitMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 认养文案合规校验（对应 mock-server/src/compliance.ts）。
 * 认养是实物交付，不能写成理财：命中金融类词语就拦截，并给出改写建议。
 */
@Service
public class FarmComplianceService {

    public static final List<String> BANNED_WORDS = Arrays.asList(
            "收益", "回报", "利息", "稳赚", "返利", "回购", "分红", "保底", "年化", "投资", "拉新提成");

    public static final String SUGGESTION = "建议改为实物交付的说法，例如「约定交付 240 斤，不足部分按同等品质补发」";

    @Resource
    private FarmComplianceHitMapper hitMapper;

    /** 把非空文本拼起来，按词表顺序返回命中的词 */
    public static List<String> findBannedWords(Collection<String> texts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String t : texts) {
            if (StrUtil.isEmpty(t)) continue;
            if (!first) sb.append('\n');
            sb.append(t);
            first = false;
        }
        String all = sb.toString();
        List<String> out = new ArrayList<>();
        for (String w : BANNED_WORDS) {
            if (all.contains(w)) out.add(w);
        }
        return out;
    }

    public static String bannedMessage(List<String> words) {
        return "文案含有金融类违禁词：" + String.join("、", words) + "。" + SUGGESTION;
    }

    /** 记一条被拦截的发布（风险自检用）。调用方不要包在会回滚的事务里 */
    public void logHit(Long productId, String productTitle, List<String> words) {
        FarmComplianceHitDO h = new FarmComplianceHitDO();
        h.setProductId(productId);
        h.setProductTitle(productTitle == null ? "" : productTitle);
        h.setWords(words);
        hitMapper.insert(h);
    }
}
