package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticlePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleSaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleCategoryDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleCategoryMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 文章（契约 2.11；对应 mock-server/src/routes/content.ts 第 52–97 行）。
 * 按租户隔离由租户拦截器自动处理；平台（租户 1）发布的文章属于平台。
 */
@Service
public class FarmArticleService {

    @Resource
    private FarmArticleMapper articleMapper;
    @Resource
    private FarmArticleCategoryMapper categoryMapper;

    public PageResult<FarmArticleDO> page(FarmArticlePageReqVO req) {
        return articleMapper.selectPage(req, new LambdaQueryWrapperX<FarmArticleDO>()
                .likeIfPresent(FarmArticleDO::getTitle, req.getTitle())
                .likeIfPresent(FarmArticleDO::getAuthor, req.getAuthor())
                .eqIfPresent(FarmArticleDO::getCategoryId, req.getCategoryId())
                .eqIfPresent(FarmArticleDO::getStatus, req.getStatus())
                .orderByDesc(FarmArticleDO::getCreateTime)
                .orderByDesc(FarmArticleDO::getId));
    }

    public FarmArticleDO get(Long id) {
        FarmArticleDO a = id == null ? null : articleMapper.selectById(id);
        if (a == null) throw exception(NOT_FOUND, "文章");
        return a;
    }

    public Long create(FarmArticleSaveReqVO req) {
        checkArticle(req.getTitle(), req.getCategoryId());
        FarmArticleDO a = new FarmArticleDO();
        a.setCategoryId(req.getCategoryId());
        a.setTitle(req.getTitle().trim());
        a.setAuthor(req.getAuthor() != null ? req.getAuthor() : StrUtil.nullToEmpty(SecurityFrameworkUtils.getLoginUserNickname()));
        a.setPicUrl(StrUtil.nullToEmpty(req.getPicUrl()));
        a.setIntroduction(StrUtil.nullToEmpty(req.getIntroduction()));
        a.setContent(StrUtil.nullToEmpty(req.getContent()));
        a.setSort(req.getSort() == null ? 0 : req.getSort());
        a.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        a.setBrowseCount(0);
        a.setRecommendHot(Boolean.TRUE.equals(req.getRecommendHot()));
        a.setRecommendBanner(Boolean.TRUE.equals(req.getRecommendBanner()));
        a.setSpuId(req.getSpuId() == null ? 0L : req.getSpuId());
        articleMapper.insert(a);
        return a.getId();
    }

    public void update(FarmArticleSaveReqVO req) {
        FarmArticleDO a = get(req.getId());
        // 先把请求里传了的字段合并进去再校验（与 mock 一致）
        String title = req.getTitle() != null ? req.getTitle() : a.getTitle();
        Long categoryId = req.getCategoryId() != null ? req.getCategoryId() : a.getCategoryId();
        checkArticle(title, categoryId);
        a.setCategoryId(categoryId);
        a.setTitle(title.trim());
        if (req.getAuthor() != null) a.setAuthor(req.getAuthor());
        if (req.getPicUrl() != null) a.setPicUrl(req.getPicUrl());
        if (req.getIntroduction() != null) a.setIntroduction(req.getIntroduction());
        if (req.getContent() != null) a.setContent(req.getContent());
        if (req.getSort() != null) a.setSort(req.getSort());
        if (req.getStatus() != null) a.setStatus(req.getStatus());
        if (req.getRecommendHot() != null) a.setRecommendHot(req.getRecommendHot());
        if (req.getRecommendBanner() != null) a.setRecommendBanner(req.getRecommendBanner());
        if (req.getSpuId() != null) a.setSpuId(req.getSpuId());
        articleMapper.updateById(a);
    }

    public void delete(Long id) {
        FarmArticleDO a = get(id);
        articleMapper.deleteById(a.getId());
    }

    private void checkArticle(String title, Long categoryId) {
        if (StrUtil.isBlank(title)) throw exception(BAD_REQUEST, "标题不能为空");
        // 分类必须是本租户的（租户拦截器自动加 tenant_id）
        if (categoryId == null || categoryMapper.selectCount(FarmArticleCategoryDO::getId, categoryId) == 0) {
            throw exception(BAD_REQUEST, "请选择文章分类");
        }
    }
}
