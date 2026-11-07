package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleCategoryPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.article.vo.FarmArticleCategorySaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleCategoryDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmArticleDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleCategoryMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmArticleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 文章分类（契约 2.11；对应 mock-server/src/routes/content.ts 第 14–50 行）。按租户隔离由租户拦截器自动处理 */
@Service
public class FarmArticleCategoryService {

    @Resource
    private FarmArticleCategoryMapper categoryMapper;
    @Resource
    private FarmArticleMapper articleMapper;

    public PageResult<FarmArticleCategoryDO> page(FarmArticleCategoryPageReqVO req) {
        return categoryMapper.selectPage(req, new LambdaQueryWrapperX<FarmArticleCategoryDO>()
                .likeIfPresent(FarmArticleCategoryDO::getName, req.getName())
                .eqIfPresent(FarmArticleCategoryDO::getStatus, req.getStatus())
                .orderByAsc(FarmArticleCategoryDO::getSort)
                .orderByAsc(FarmArticleCategoryDO::getId));
    }

    /** 下拉用，只返回启用的分类 */
    public List<FarmArticleCategoryDO> listEnabled() {
        return categoryMapper.selectList(new LambdaQueryWrapperX<FarmArticleCategoryDO>()
                .eq(FarmArticleCategoryDO::getStatus, 0)
                .orderByAsc(FarmArticleCategoryDO::getId));
    }

    public FarmArticleCategoryDO get(Long id) {
        FarmArticleCategoryDO c = id == null ? null : categoryMapper.selectById(id);
        if (c == null) throw exception(NOT_FOUND, "文章分类");
        return c;
    }

    public Long create(FarmArticleCategorySaveReqVO req) {
        if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "分类名称不能为空");
        FarmArticleCategoryDO c = new FarmArticleCategoryDO();
        c.setName(req.getName().trim());
        c.setPicUrl(StrUtil.nullToEmpty(req.getPicUrl()));
        c.setSort(req.getSort() == null ? 0 : req.getSort());
        c.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        categoryMapper.insert(c);
        return c.getId();
    }

    public void update(FarmArticleCategorySaveReqVO req) {
        FarmArticleCategoryDO c = get(req.getId());
        if (req.getName() != null) {
            if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "分类名称不能为空");
            c.setName(req.getName().trim());
        }
        if (req.getPicUrl() != null) c.setPicUrl(req.getPicUrl());
        if (req.getSort() != null) c.setSort(req.getSort());
        if (req.getStatus() != null) c.setStatus(req.getStatus());
        categoryMapper.updateById(c);
    }

    public void delete(Long id) {
        FarmArticleCategoryDO c = get(id);
        if (articleMapper.selectCount(FarmArticleDO::getCategoryId, c.getId()) > 0) throw exception(BAD_REQUEST, "分类下还有文章，不能删除");
        categoryMapper.deleteById(c.getId());
    }
}
