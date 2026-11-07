package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.PRODUCT_BANNED_WORDS;

/**
 * 命中违禁词。芋道的 ServiceException 带不了 data，而契约要求响应 data 里有 words 和 suggestion，
 * 所以用这个异常把命中词带到 Controller，由 Controller 捕获后用 {@link #toResult()} 返回。
 */
public class FarmBannedWordsException extends RuntimeException {

    private final List<String> words;

    public FarmBannedWordsException(List<String> words) {
        super(FarmComplianceService.bannedMessage(words));
        this.words = words;
    }

    public List<String> getWords() {
        return words;
    }

    /** {code:1030000000, msg:"文案含有…", data:{words, suggestion}} */
    public CommonResult<Object> toResult() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("words", words);
        data.put("suggestion", FarmComplianceService.SUGGESTION);
        CommonResult<Object> r = CommonResult.error(PRODUCT_BANNED_WORDS.getCode(), getMessage());
        r.setData(data);
        return r;
    }
}
