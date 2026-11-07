package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductSaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 认养商品（契约 2.4；mock 见 farm-admin.ts 178-240）。
 * 注意：命中违禁词要先落一条 farm_compliance_hit 再抛异常，所以这里的方法不能加 @Transactional（否则命中记录会跟着回滚）。
 */
@Service
public class FarmProductService {

    /** 资产类型 -> 认养分类名（与 mock views.ts 的 CATEGORY_NAME 一致） */
    private static final Map<String, String> CATEGORY_NAME = Map.of("plot", "土地认养", "animal", "家禽认养", "tree", "果树认养");

    @Autowired
    private FarmProductMapper productMapper;
    @Autowired
    private FarmBaseMapper baseMapper;
    @Autowired
    private FarmComplianceService complianceService;

    public PageResult<FarmProductRespVO> page(FarmProductPageReqVO req) {
        PageResult<FarmProductDO> p = productMapper.selectPage(req, new LambdaQueryWrapperX<FarmProductDO>()
                .likeIfPresent(FarmProductDO::getTitle, req.getTitle())
                .eqIfPresent(FarmProductDO::getStatus, req.getStatus())
                .eqIfPresent(FarmProductDO::getBaseId, req.getBaseId())
                .eqIfPresent(FarmProductDO::getAssetType, req.getAssetType())
                .geIfPresent(FarmProductDO::getBasePrice, req.getPriceMin())
                .leIfPresent(FarmProductDO::getBasePrice, req.getPriceMax())
                .orderByDesc(FarmProductDO::getCreateTime)
                .orderByDesc(FarmProductDO::getId));
        return new PageResult<>(toViews(p.getList()), p.getTotal());
    }

    public FarmProductDO get(Long id) {
        FarmProductDO p = id == null ? null : productMapper.selectById(id);
        if (p == null) throw exception(NOT_FOUND, "认养商品");
        return p;
    }

    public FarmProductRespVO getView(Long id) {
        return toViews(List.of(get(id))).get(0);
    }

    public Long create(FarmProductSaveReqVO req) {
        FarmBaseDO base = req.getBaseId() == null ? null : baseMapper.selectById(req.getBaseId());
        if (base == null) throw exception(BAD_REQUEST, "请选择基地");
        List<Map<String, Object>> modes = req.getCareModes() == null ? new ArrayList<>() : req.getCareModes();
        check(null, req.getTitle(), req.getDescription(), modes);

        FarmProductDO p = new FarmProductDO();
        p.setBaseId(base.getId());
        p.setAssetType(req.getAssetType() == null ? "plot" : req.getAssetType());
        p.setTitle(req.getTitle().trim());
        p.setCover(req.getCover() == null ? "" : req.getCover());
        p.setDescription(req.getDescription() == null ? "" : req.getDescription());
        p.setCycleDays(req.getCycleDays() == null || req.getCycleDays() == 0 ? 365 : req.getCycleDays());
        p.setAreaM2(req.getAreaM2());
        // create：优先 full 档（不看是否开放），没有 full 档取第一个已开放档
        Map<String, Object> full = findMode(modes, "full", false);
        p.setBasePrice(toLong((full != null ? full : firstEnabled(modes)).get("price")));
        p.setStock(req.getStock() == null ? 0 : req.getStock());
        p.setSold(0);
        p.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        p.setCareModes(modes);
        productMapper.insert(p);
        return p.getId();
    }

    public void update(FarmProductSaveReqVO req) {
        FarmProductDO p = get(req.getId());
        // 先按「已有值 + 请求值」合并后校验，和 mock 的 merged 一致
        String title = req.getTitle() != null ? req.getTitle() : p.getTitle();
        String description = req.getDescription() != null ? req.getDescription() : p.getDescription();
        List<Map<String, Object>> modes = req.getCareModes() != null ? req.getCareModes() : p.getCareModes();
        if (modes == null) modes = new ArrayList<>();
        check(p.getId(), title, description, modes);

        if (req.getTitle() != null) p.setTitle(req.getTitle());
        if (req.getCover() != null) p.setCover(req.getCover());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getCycleDays() != null) p.setCycleDays(req.getCycleDays());
        if (req.getAreaM2() != null) p.setAreaM2(req.getAreaM2());
        if (req.getStock() != null) p.setStock(req.getStock());
        if (req.getCareModes() != null) p.setCareModes(req.getCareModes());
        if (req.getBaseId() != null) p.setBaseId(req.getBaseId());
        if (req.getAssetType() != null) p.setAssetType(req.getAssetType());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        // update：优先已开放的 full 档，否则第一个已开放档
        Map<String, Object> full = findMode(modes, "full", true);
        p.setBasePrice(toLong((full != null ? full : firstEnabled(modes)).get("price")));
        productMapper.updateById(p);
    }

    public void updateStatus(Long id, Integer status) {
        FarmProductDO p = get(id);
        p.setStatus(status != null && status == 1 ? 1 : 0);
        productMapper.updateById(p);
    }

    /** 对应 mock 的 checkProduct；命中违禁词时记一条再抛 {@link FarmBannedWordsException} */
    private void check(Long productId, String title, String description, List<Map<String, Object>> modes) {
        if (StrUtil.isBlank(title)) throw exception(BAD_REQUEST, "标题不能为空");
        if (modes.stream().noneMatch(FarmProductService::isEnabled)) throw exception(BAD_REQUEST, "至少开放一种托管方式");
        for (Map<String, Object> m : modes) {
            if (isEnabled(m) && !(toDouble(m.get("price")) > 0)) throw exception(BAD_REQUEST, "开放的托管方式要填价格");
        }
        List<String> texts = new ArrayList<>();
        texts.add(title);
        texts.add(description);
        for (Map<String, Object> m : modes) texts.add(str(m.get("outputPromise")));
        for (Map<String, Object> m : modes) texts.add(str(m.get("slogan")));
        List<String> words = FarmComplianceService.findBannedWords(texts);
        if (!words.isEmpty()) {
            complianceService.logHit(productId, title, words);
            throw new FarmBannedWordsException(words);
        }
    }

    // ---------- view ----------

    private List<FarmProductRespVO> toViews(List<FarmProductDO> list) {
        List<FarmProductRespVO> out = new ArrayList<>();
        if (CollUtil.isEmpty(list)) return out;
        Set<Long> baseIds = list.stream().map(FarmProductDO::getBaseId).collect(Collectors.toSet());
        Map<Long, String> baseNames = new HashMap<>();
        for (FarmBaseDO b : baseMapper.selectList(FarmBaseDO::getId, baseIds)) baseNames.put(b.getId(), b.getName());
        for (FarmProductDO p : list) {
            FarmProductRespVO v = BeanUtils.toBean(p, FarmProductRespVO.class);
            v.setBaseName(baseNames.getOrDefault(p.getBaseId(), ""));
            Long min = null;
            List<String> modeNames = new ArrayList<>();
            boolean insured = false;
            String expectedOutput = null;
            if (p.getCareModes() != null) {
                for (Map<String, Object> m : p.getCareModes()) {
                    if (!isEnabled(m)) continue;
                    long price = toLong(m.get("price"));
                    if (min == null || price < min) min = price;
                    modeNames.add(str(m.get("mode")));
                    String insurance = str(m.get("insurance"));
                    if (insurance != null && insurance.contains("含")) insured = true;
                    if (expectedOutput == null || "full".equals(str(m.get("mode")))) expectedOutput = str(m.get("outputPromise"));
                }
            }
            int stock = p.getStock() == null ? 0 : p.getStock();
            int sold = p.getSold() == null ? 0 : p.getSold();
            int totalSlots = stock + sold;
            v.setNo("P" + String.format("%03d", p.getId()));
            v.setCategoryName(CATEGORY_NAME.getOrDefault(p.getAssetType(), ""));
            v.setTotalSlots(totalSlots);
            v.setSoldRate(totalSlots > 0 ? (int) Math.round(sold * 100.0 / totalSlots) : 0);
            v.setInsured(insured);
            v.setExpectedOutput(expectedOutput == null ? "" : expectedOutput);
            v.setMinPrice(min);
            v.setEnabledModes(modeNames);
            out.add(v);
        }
        return out;
    }

    // ---------- helpers ----------

    private static boolean isEnabled(Map<String, Object> m) {
        return m != null && Boolean.TRUE.equals(m.get("enabled"));
    }

    private static Map<String, Object> findMode(List<Map<String, Object>> modes, String mode, boolean mustEnabled) {
        for (Map<String, Object> m : modes) {
            if (m != null && mode.equals(m.get("mode")) && (!mustEnabled || isEnabled(m))) return m;
        }
        return null;
    }

    /** check() 已保证至少有一个开放档位 */
    private static Map<String, Object> firstEnabled(List<Map<String, Object>> modes) {
        for (Map<String, Object> m : modes) if (isEnabled(m)) return m;
        throw exception(BAD_REQUEST, "至少开放一种托管方式");
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o == null) return 0;
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long toLong(Object o) {
        return (long) toDouble(o);
    }
}
