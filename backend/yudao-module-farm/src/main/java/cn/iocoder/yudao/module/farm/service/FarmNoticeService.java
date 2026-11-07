package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.farm.controller.admin.notice.vo.FarmNoticeListRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.notice.vo.FarmNoticeRespVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleCategoryDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleCategoryMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 平台公告：平台租户（id=1）在文章管理里发布、状态为 0 的文章，给所有经营者看（mock：farm-admin.ts 421–432 行）。
 * 文章表按租户隔离，这里用 TenantUtils.execute 切到平台租户查询。
 */
@Service
public class FarmNoticeService {

    private static final Long PLATFORM_TENANT_ID = 1L;

    @Autowired
    private FarmArticleMapper articleMapper;
    @Autowired
    private FarmArticleCategoryMapper categoryMapper;

    public List<FarmNoticeListRespVO> list() {
        return TenantUtils.execute(PLATFORM_TENANT_ID, () -> {
            List<FarmArticleDO> articles = articleMapper.selectList(new LambdaQueryWrapperX<FarmArticleDO>()
                    .eq(FarmArticleDO::getStatus, 0)
                    .orderByDesc(FarmArticleDO::getCreateTime)
                    .orderByDesc(FarmArticleDO::getId));
            Map<Long, String> names = categoryNames(articles);
            List<FarmNoticeListRespVO> out = new ArrayList<>();
            for (FarmArticleDO a : articles) {
                FarmNoticeListRespVO v = new FarmNoticeListRespVO();
                v.setId(a.getId());
                v.setTitle(a.getTitle());
                v.setIntroduction(a.getIntroduction());
                v.setCreateTime(a.getCreateTime());
                v.setCategoryName(names.getOrDefault(a.getCategoryId(), ""));
                out.add(v);
            }
            return out;
        });
    }

    public FarmNoticeRespVO get(Long id) {
        FarmNoticeRespVO v = TenantUtils.execute(PLATFORM_TENANT_ID, () -> {
            FarmArticleDO a = id == null ? null : articleMapper.selectOne(new LambdaQueryWrapperX<FarmArticleDO>()
                    .eq(FarmArticleDO::getId, id)
                    .eq(FarmArticleDO::getStatus, 0));
            if (a == null) return null;
            FarmNoticeRespVO r = BeanUtils.toBean(a, FarmNoticeRespVO.class);
            r.setCategoryName(categoryNames(List.of(a)).getOrDefault(a.getCategoryId(), ""));
            return r;
        });
        if (v == null) throw exception(NOT_FOUND, "公告");
        return v;
    }

    private Map<Long, String> categoryNames(List<FarmArticleDO> articles) {
        List<Long> ids = articles.stream().map(FarmArticleDO::getCategoryId).filter(i -> i != null).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return new java.util.HashMap<>();
        Map<Long, String> out = new java.util.HashMap<>();
        for (FarmArticleCategoryDO c : categoryMapper.selectList(FarmArticleCategoryDO::getId, ids)) {
            out.put(c.getId(), c.getName() == null ? "" : c.getName());
        }
        return out;
    }
}
