package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.base.vo.FarmBasePageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.base.vo.FarmBaseRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.base.vo.FarmBaseSaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmStewardDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmStewardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/**
 * 基地。数据按租户隔离由芋道的租户拦截器自动加 tenant_id 条件（不用手写）；
 * 跨租户按 id 取，查不到就是「不存在」，不暴露别的租户的数据（契约 0 通用约定）。
 */
@Service
public class FarmBaseService {

    @Autowired
    private FarmBaseMapper baseMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmStewardMapper stewardMapper;

    public PageResult<FarmBaseRespVO> page(FarmBasePageReqVO req) {
        PageResult<FarmBaseDO> p = baseMapper.selectPage(req, new LambdaQueryWrapperX<FarmBaseDO>()
                .likeIfPresent(FarmBaseDO::getName, req.getName())
                .eqIfPresent(FarmBaseDO::getStatus, req.getStatus())
                .orderByDesc(FarmBaseDO::getId));
        PageResult<FarmBaseRespVO> out = BeanUtils.toBean(p, FarmBaseRespVO.class);
        for (FarmBaseRespVO v : out.getList()) {
            v.setAssetCount(assetMapper.selectCount(FarmAssetDO::getBaseId, v.getId()));
            v.setStewardCount(stewardMapper.selectCount(FarmStewardDO::getBaseId, v.getId()));
        }
        return out;
    }

    /** 下拉框用，只返回启用的基地 */
    public List<FarmBaseDO> listEnabled() {
        return baseMapper.selectList(new LambdaQueryWrapperX<FarmBaseDO>().eq(FarmBaseDO::getStatus, 0).orderByAsc(FarmBaseDO::getId));
    }

    public FarmBaseDO get(Long id) {
        FarmBaseDO b = id == null ? null : baseMapper.selectById(id);
        if (b == null) throw exception(NOT_FOUND, "基地");
        return b;
    }

    public Long create(FarmBaseSaveReqVO req) {
        if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "基地名称不能为空");
        if (StrUtil.isBlank(req.getAddress())) throw exception(BAD_REQUEST, "基地地址不能为空");
        FarmBaseDO b = new FarmBaseDO();
        b.setName(req.getName().trim());
        b.setAddress(req.getAddress().trim());
        b.setLng(nz(req.getLng()));
        b.setLat(nz(req.getLat()));
        b.setAreaMu(nz(req.getAreaMu()));
        b.setCertNo(StrUtil.nullToEmpty(req.getCertNo()));
        b.setCropSummary(StrUtil.nullToEmpty(req.getCropSummary()));
        b.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        b.setCover("");
        baseMapper.insert(b);
        return b.getId();
    }

    public void update(FarmBaseSaveReqVO req) {
        FarmBaseDO b = get(req.getId());
        if (req.getName() != null) {
            if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "基地名称不能为空");
            b.setName(req.getName().trim());
        }
        if (req.getAddress() != null) {
            if (StrUtil.isBlank(req.getAddress())) throw exception(BAD_REQUEST, "基地地址不能为空");
            b.setAddress(req.getAddress().trim());
        }
        if (req.getLng() != null) b.setLng(req.getLng());
        if (req.getLat() != null) b.setLat(req.getLat());
        if (req.getAreaMu() != null) b.setAreaMu(req.getAreaMu());
        if (req.getCertNo() != null) b.setCertNo(req.getCertNo());
        if (req.getCropSummary() != null) b.setCropSummary(req.getCropSummary());
        if (req.getStatus() != null) b.setStatus(req.getStatus());
        baseMapper.updateById(b);
    }

    public void delete(Long id) {
        FarmBaseDO b = get(id);
        if (assetMapper.selectCount(FarmAssetDO::getBaseId, b.getId()) > 0) throw exception(BAD_REQUEST, "基地下还有资产，不能删除");
        baseMapper.deleteById(b.getId());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
