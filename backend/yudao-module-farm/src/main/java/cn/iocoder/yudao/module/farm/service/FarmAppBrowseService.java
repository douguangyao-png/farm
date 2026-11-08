package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplyRespVO;
import cn.iocoder.yudao.module.farm.controller.app.article.vo.FarmAppArticleDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.app.article.vo.FarmAppArticleRespVO;
import cn.iocoder.yudao.module.farm.controller.app.home.vo.FarmAppHomeRespVO;
import cn.iocoder.yudao.module.farm.controller.app.home.vo.FarmAppProductDetailRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleCategoryDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleCategoryMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * C 端浏览类接口（不需要登录，但要带 tenant-id）：首页、商品详情、农资、农场资讯。
 * mock：farm-app.ts 17–58、280–292 行；商品视图见 views.ts productView。
 * 租户隔离由芋道拦截器自动追加 tenant_id（对应 mock 的 own() / 按租户过滤）。
 */
@Service
public class FarmAppBrowseService {

    @Autowired
    private FarmBaseMapper baseMapper;
    @Autowired
    private FarmProductMapper productMapper;
    @Autowired
    private FarmSupplyMapper supplyMapper;
    @Autowired
    private FarmArticleMapper articleMapper;
    @Autowired
    private FarmArticleCategoryMapper categoryMapper;
    @Autowired
    private TenantService tenantService;

    // ---------- 首页 ----------

    public FarmAppHomeRespVO home() {
        FarmAppHomeRespVO v = new FarmAppHomeRespVO();
        TenantDO tenant = tenantService.getTenant(TenantContextHolder.getRequiredTenantId());
        v.setTenantName(tenant == null || tenant.getName() == null ? "" : tenant.getName());

        List<Map<String, Object>> banners = new ArrayList<>();
        banners.add(banner(FarmAppSupport.placeholderImage("一块自己的菜地", "#2e7d32"), "一分菜地 · 年度认养", "/pages/farm/product/detail?id=1"));
        banners.add(banner(FarmAppSupport.placeholderImage("林下跑山鸡", "#f9a825"), "安吉跑山鸡 · 认养一只", "/pages/farm/product/detail?id=2"));
        v.setBanners(banners);

        List<FarmBaseDO> bases = baseMapper.selectList(new LambdaQueryWrapper<FarmBaseDO>()
                .eq(FarmBaseDO::getStatus, 0).orderByAsc(FarmBaseDO::getId));
        List<FarmAppHomeRespVO.Base> baseVos = new ArrayList<>();
        for (FarmBaseDO b : bases) {
            FarmAppHomeRespVO.Base bv = new FarmAppHomeRespVO.Base();
            bv.setId(b.getId());
            bv.setName(b.getName());
            bv.setCover(b.getCover());
            bv.setAddress(b.getAddress());
            baseVos.add(bv);
        }
        v.setBases(baseVos);

        List<FarmProductDO> products = productMapper.selectList(new LambdaQueryWrapper<FarmProductDO>()
                .eq(FarmProductDO::getStatus, 0).orderByAsc(FarmProductDO::getId));
        v.setProducts(productViews(products));

        // 农场资讯：只放本农场已发布（status=0）的文章；平台的公告、帮助是给经营者看的，在后台工作台展示
        List<FarmArticleDO> articles = articleMapper.selectList(new LambdaQueryWrapper<FarmArticleDO>()
                .eq(FarmArticleDO::getStatus, 0)
                .orderByDesc(FarmArticleDO::getCreateTime).orderByDesc(FarmArticleDO::getId)
                .last("LIMIT 5"));
        List<FarmAppHomeRespVO.Article> artVos = new ArrayList<>();
        for (FarmArticleDO a : articles) {
            FarmAppHomeRespVO.Article av = new FarmAppHomeRespVO.Article();
            av.setId(a.getId());
            av.setTitle(a.getTitle());
            av.setIntroduction(a.getIntroduction());
            av.setPicUrl(a.getPicUrl());
            av.setCreateTime(a.getCreateTime());
            artVos.add(av);
        }
        v.setArticles(artVos);
        return v;
    }

    private static Map<String, Object> banner(String image, String title, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("image", image);
        m.put("title", title);
        m.put("url", url);
        return m;
    }

    // ---------- 商品 ----------

    public FarmAppProductDetailRespVO getProduct(Long id) {
        FarmProductDO p = id == null ? null : productMapper.selectById(id);
        if (p == null || p.getStatus() == null || p.getStatus() != 0) throw exception(NOT_FOUND, "认养商品");
        FarmBaseDO base = p.getBaseId() == null ? null : baseMapper.selectById(p.getBaseId());
        FarmAppProductDetailRespVO v = BeanUtils.toBean(p, FarmAppProductDetailRespVO.class);
        fillView(v, p, base == null || base.getName() == null ? "" : base.getName());
        if (base != null) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("name", base.getName());
            b.put("address", base.getAddress());
            b.put("certNo", base.getCertNo());
            v.setBase(b);
        }
        v.setCompareRows(compareRows(p.getCareModes()));
        v.setSupplyPackages(supplyPackages(p.getCareModes()));
        return v;
    }

    private static final List<String> CAT_ORDER = java.util.Arrays.asList("seed", "fert", "tool", "bio", "svc", "ins");

    /** 农资套餐：口径同后台 hosting-config 的 modes[].supplyPackage（在售、可零售、适配该托管方式，按品类排序，第一个推荐；full 为空） */
    private List<Map<String, Object>> supplyPackages(List<Map<String, Object>> modes) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (modes == null || modes.isEmpty()) return out;
        List<FarmSupplyDO> supplies = supplyMapper.selectList(new LambdaQueryWrapper<FarmSupplyDO>().orderByAsc(FarmSupplyDO::getId)).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 0 && !Boolean.FALSE.equals(s.getRetailAllowed())).collect(Collectors.toList());
        for (Map<String, Object> c : modes) {
            String mode = String.valueOf(c.get("mode"));
            List<Map<String, Object>> items = new ArrayList<>();
            if (!"full".equals(mode)) {
                List<FarmSupplyDO> fit = supplies.stream().filter(s -> s.getFitModes() != null && s.getFitModes().contains(mode))
                        .sorted(java.util.Comparator.comparingInt((FarmSupplyDO s) -> CAT_ORDER.indexOf(s.getCat())).thenComparing(FarmSupplyDO::getId))
                        .collect(Collectors.toList());
                for (int i = 0; i < fit.size(); i++) {
                    FarmSupplyDO s = fit.get(i);
                    Map<String, Object> it = new LinkedHashMap<>();
                    it.put("id", s.getId());
                    it.put("name", s.getName());
                    it.put("cat", s.getCat());
                    it.put("price", s.getPrice());
                    it.put("unit", s.getUnit());
                    it.put("recommended", i == 0);
                    items.add(it);
                }
            }
            Map<String, Object> pk = new LinkedHashMap<>();
            pk.put("mode", mode);
            pk.put("items", items);
            out.add(pk);
        }
        return out;
    }

    /** 托管对比表：以第一档的责任清单为行，每个档位一列（mock：farm-app.ts 42–45 行） */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> compareRows(List<Map<String, Object>> modes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (modes == null || modes.isEmpty()) return rows;
        Object firstDuty = modes.get(0).get("duty");
        if (!(firstDuty instanceof List)) return rows;
        List<Object> duties = (List<Object>) firstDuty;
        for (int i = 0; i < duties.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            Object d = duties.get(i);
            row.put("item", d instanceof Map ? ((Map<String, Object>) d).get("item") : null);
            for (Map<String, Object> m : modes) {
                String text;
                if (!Boolean.TRUE.equals(m.get("enabled"))) {
                    text = "—";
                } else {
                    Object md = m.get("duty");
                    Object di = md instanceof List && i < ((List<Object>) md).size() ? ((List<Object>) md).get(i) : null;
                    boolean member = di instanceof Map && "member".equals(((Map<String, Object>) di).get("owner"));
                    text = member ? "您来做" : "农场做";
                }
                row.put(String.valueOf(m.get("mode")), text);
            }
            rows.add(row);
        }
        return rows;
    }

    /** 对应 mock productView：基地名、已开放档位的最低价、已开放档位列表 */
    private List<FarmProductRespVO> productViews(List<FarmProductDO> list) {
        List<FarmProductRespVO> out = new ArrayList<>();
        if (list.isEmpty()) return out;
        Set<Long> baseIds = list.stream().map(FarmProductDO::getBaseId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!baseIds.isEmpty()) {
            for (FarmBaseDO b : baseMapper.selectList(FarmBaseDO::getId, baseIds)) names.put(b.getId(), b.getName());
        }
        for (FarmProductDO p : list) {
            FarmProductRespVO v = BeanUtils.toBean(p, FarmProductRespVO.class);
            fillView(v, p, names.getOrDefault(p.getBaseId(), ""));
            out.add(v);
        }
        return out;
    }

    private static void fillView(FarmProductRespVO v, FarmProductDO p, String baseName) {
        v.setBaseName(baseName == null ? "" : baseName);
        Long min = null;
        List<String> enabled = new ArrayList<>();
        if (p.getCareModes() != null) {
            for (Map<String, Object> m : p.getCareModes()) {
                if (m == null || !Boolean.TRUE.equals(m.get("enabled"))) continue;
                Object price = m.get("price");
                long pr = price instanceof Number ? ((Number) price).longValue() : 0L;
                if (min == null || pr < min) min = pr;
                enabled.add(String.valueOf(m.get("mode")));
            }
        }
        v.setMinPrice(min);
        v.setEnabledModes(enabled);
    }

    // ---------- 农资（只给零售的、上架的） ----------

    public List<FarmSupplyRespVO> supplyList(String cat) {
        LambdaQueryWrapper<FarmSupplyDO> w = new LambdaQueryWrapper<FarmSupplyDO>()
                .eq(FarmSupplyDO::getStatus, 0)
                .eq(FarmSupplyDO::getRetailAllowed, true);
        if (StrUtil.isNotEmpty(cat)) w.eq(FarmSupplyDO::getCat, cat);
        w.orderByAsc(FarmSupplyDO::getId);
        return supplyViews(supplyMapper.selectList(w));
    }

    public FarmSupplyRespVO getSupply(Long id) {
        FarmSupplyDO s = id == null ? null : supplyMapper.selectById(id);
        if (s == null || s.getStatus() == null || s.getStatus() != 0 || !Boolean.TRUE.equals(s.getRetailAllowed())) {
            throw exception(NOT_FOUND, "农资");
        }
        return supplyViews(List.of(s)).get(0);
    }

    /** 成本价是经营者的内部数据，不返回给消费者（mock 整条返回，字段保留但置空，前端不依赖） */
    private static List<FarmSupplyRespVO> supplyViews(List<FarmSupplyDO> list) {
        List<FarmSupplyRespVO> out = BeanUtils.toBean(list, FarmSupplyRespVO.class);
        for (FarmSupplyRespVO v : out) v.setCost(null);
        return out;
    }

    // ---------- 农场资讯 ----------

    public List<FarmAppArticleRespVO> articleList() {
        List<FarmArticleDO> list = articleMapper.selectList(new LambdaQueryWrapper<FarmArticleDO>()
                .eq(FarmArticleDO::getStatus, 0)
                .orderByDesc(FarmArticleDO::getCreateTime).orderByDesc(FarmArticleDO::getId));
        return BeanUtils.toBean(list, FarmAppArticleRespVO.class);
    }

    public FarmAppArticleDetailRespVO getArticle(Long id) {
        FarmArticleDO a = id == null ? null : articleMapper.selectById(id);
        if (a == null || a.getStatus() == null || a.getStatus() != 0) throw exception(NOT_FOUND, "文章");
        articleMapper.update(null, new LambdaUpdateWrapper<FarmArticleDO>()
                .eq(FarmArticleDO::getId, a.getId())
                .setSql("browse_count = COALESCE(browse_count, 0) + 1"));
        FarmAppArticleDetailRespVO v = BeanUtils.toBean(a, FarmAppArticleDetailRespVO.class);
        v.setBrowseCount((a.getBrowseCount() == null ? 0 : a.getBrowseCount()) + 1);
        FarmArticleCategoryDO c = a.getCategoryId() == null ? null : categoryMapper.selectById(a.getCategoryId());
        v.setCategoryName(c == null || c.getName() == null ? "" : c.getName());
        return v;
    }
}
