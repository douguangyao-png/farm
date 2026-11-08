package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.farm.controller.app.contract.vo.FarmAppContractSignReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmOrderDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmOrderMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.CONTRACT_SIGNED;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** C 端合同：查看、手写签名（mock：routes/farm-p1b.ts 的 /contract/get、/contract/sign）。合同编号 = 订单编号 */
@Service
public class FarmAppContractService {

    private static final int SIGN_MAX_BYTES = 200 * 1024;
    private static final Pattern DATA_URL = Pattern.compile("^data:([\\w/+.-]+);base64,", Pattern.CASE_INSENSITIVE);
    private static final Pattern B64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private FarmOrderMapper orderMapper;
    @Autowired
    private FarmAppSupport support;
    @Autowired
    private FarmViewLookup lookup;

    /** 取自己的订单（别人的、不存在的都按 404） */
    private FarmOrderDO mine(Long id) {
        FarmMemberDO m = support.member();
        FarmOrderDO o = id == null ? null : orderMapper.selectById(id);
        if (o == null || !Objects.equals(o.getMemberId(), m.getId())) throw exception(NOT_FOUND, "合同");
        return o;
    }

    public Map<String, Object> get(Long orderId) {
        FarmOrderDO o = mine(orderId);
        FarmMemberDO m = support.member();
        FarmProductDO p = o.getProductId() == null ? null : lookup.products(List.of(o.getProductId())).get(o.getProductId());
        String no = o.getNo() == null ? "" : o.getNo();
        String careName = FarmViewLookup.careModeName(o.getCareMode());
        boolean signed = StrUtil.isNotEmpty(o.getSignature());
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", o.getId());
        v.put("no", "HT" + (no.length() > 2 ? no.substring(2) : ""));
        v.put("orderId", o.getId());
        v.put("orderNo", no);
        v.put("title", "《认养服务合同（" + careName + "）》" + o.getContractVersion());
        v.put("version", o.getContractVersion());
        v.put("careMode", o.getCareMode());
        v.put("careModeName", careName);
        v.put("status", statusOf(o));
        v.put("productTitle", p == null || p.getTitle() == null ? "" : p.getTitle());
        v.put("amount", o.getAmount());
        v.put("memberName", m.getNickname() == null ? "" : m.getNickname());
        v.put("createTime", o.getCreateTime());
        v.put("payTime", o.getPayTime());
        v.put("signed", signed);
        v.put("signedAt", o.getSignedAt());
        v.put("hasSignature", signed);
        v.put("signature", signed ? o.getSignature() : null);
        return v;
    }

    public Map<String, Object> sign(FarmAppContractSignReqVO req) {
        FarmOrderDO o = mine(req.getContractId() != null ? req.getContractId() : req.getOrderId());
        if (statusOf(o) == 20) throw exception(BAD_REQUEST, "合同已解除，不能签署");
        if (StrUtil.isNotEmpty(o.getSignature())) throw exception(CONTRACT_SIGNED);
        String url = normalize(req.getSignature());
        LocalDateTime now = LocalDateTime.now();
        // 条件更新：只有 signature 仍为空才写入，防并发重复签
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<FarmOrderDO>()
                .eq(FarmOrderDO::getId, o.getId()).isNull(FarmOrderDO::getSignature)
                .set(FarmOrderDO::getSignature, url).set(FarmOrderDO::getSignedAt, now));
        if (rows == 0) throw exception(CONTRACT_SIGNED);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", o.getId());
        out.put("signedAt", now);
        return out;
    }

    /** 与 mock parseSignature 一致：base64 PNG 或 data:image/png;base64,...；解码后 <= 200KB 且 PNG 文件头 */
    static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw exception(BAD_REQUEST, "请先手写签名");
        String b64 = raw.trim();
        Matcher m = DATA_URL.matcher(b64);
        if (m.find()) {
            if (!"image/png".equalsIgnoreCase(m.group(1))) throw exception(BAD_REQUEST, "签名图片必须是 PNG");
            b64 = b64.substring(m.end());
        }
        if (!B64.matcher(b64).matches() || b64.length() % 4 != 0) throw exception(BAD_REQUEST, "签名图片格式不正确");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw exception(BAD_REQUEST, "签名图片格式不正确");
        }
        if (bytes.length > SIGN_MAX_BYTES) throw exception(BAD_REQUEST, "签名图片不能超过 200KB");
        if (bytes.length < 8) throw exception(BAD_REQUEST, "签名图片必须是 PNG");
        for (int i = 0; i < 8; i++) if (bytes[i] != PNG_MAGIC[i]) throw exception(BAD_REQUEST, "签名图片必须是 PNG");
        return "data:image/png;base64," + b64;
    }

    /** 与 FarmContractService.statusOf 一致 */
    private static int statusOf(FarmOrderDO o) {
        if (o.getStatus() != null && (o.getStatus() == 30 || o.getStatus() == 50)) return 20;
        return Boolean.TRUE.equals(o.getContractAgreed()) && o.getPayTime() != null ? 10 : 0;
    }
}
