package cn.iocoder.yudao.module.farm.service;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplyPageReqVO;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplySaveReqVO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmSupplyDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmSupplyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.module.farm.enums.ErrorCodeConstants.NOT_FOUND;

/** 农资商品（契约 2.8；mock 见 farm-admin.ts 298-334） */
@Service
public class FarmSupplyService {

    @Autowired
    private FarmSupplyMapper supplyMapper;

    public PageResult<FarmSupplyDO> page(FarmSupplyPageReqVO req) {
        return supplyMapper.selectPage(req, new LambdaQueryWrapperX<FarmSupplyDO>()
                .likeIfPresent(FarmSupplyDO::getName, req.getName())
                .eqIfPresent(FarmSupplyDO::getCat, req.getCat())
                .eqIfPresent(FarmSupplyDO::getStatus, req.getStatus())
                .orderByAsc(FarmSupplyDO::getId));
    }

    public FarmSupplyDO get(Long id) {
        FarmSupplyDO s = id == null ? null : supplyMapper.selectById(id);
        if (s == null) throw exception(NOT_FOUND, "农资");
        return s;
    }

    public Long create(FarmSupplySaveReqVO req) {
        if (StrUtil.isBlank(req.getName())) throw exception(BAD_REQUEST, "名称不能为空");
        if (StrUtil.isEmpty(req.getCat())) throw exception(BAD_REQUEST, "请选择类目");
        if (req.getPrice() == null || req.getPrice() <= 0) throw exception(BAD_REQUEST, "价格要大于 0");
        checkWords(req);

        FarmSupplyDO s = new FarmSupplyDO();
        s.setName(req.getName().trim());
        s.setCat(req.getCat());
        s.setCover(req.getCover() == null ? "" : req.getCover());
        s.setPrice(req.getPrice());
        s.setCost(req.getCost() == null ? 0L : req.getCost());
        s.setStock(req.getStock() == null ? 0 : req.getStock());
        s.setUnit(req.getUnit() == null ? "件" : req.getUnit());
        s.setSpec(req.getSpec() == null ? "" : req.getSpec());
        s.setFitModes(req.getFitModes() == null ? new ArrayList<>(Arrays.asList("semi", "self")) : req.getFitModes());
        s.setBatchNo(req.getBatchNo() == null ? "" : req.getBatchNo());
        s.setRetailAllowed(!Boolean.FALSE.equals(req.getRetailAllowed()));
        s.setNeedWorkorder(Boolean.TRUE.equals(req.getNeedWorkorder()));
        s.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        s.setSold(0);
        s.setDescription(req.getDescription() == null ? "" : req.getDescription());
        normalize(s);
        supplyMapper.insert(s);
        return s.getId();
    }

    public void update(FarmSupplySaveReqVO req) {
        FarmSupplyDO s = get(req.getId());
        checkWords(req);
        if (req.getName() != null) s.setName(req.getName());
        if (req.getCat() != null) s.setCat(req.getCat());
        if (req.getCover() != null) s.setCover(req.getCover());
        if (req.getPrice() != null) s.setPrice(req.getPrice());
        if (req.getCost() != null) s.setCost(req.getCost());
        if (req.getStock() != null) s.setStock(req.getStock());
        if (req.getUnit() != null) s.setUnit(req.getUnit());
        if (req.getSpec() != null) s.setSpec(req.getSpec());
        if (req.getFitModes() != null) s.setFitModes(req.getFitModes());
        if (req.getBatchNo() != null) s.setBatchNo(req.getBatchNo());
        if (req.getRetailAllowed() != null) s.setRetailAllowed(req.getRetailAllowed());
        if (req.getNeedWorkorder() != null) s.setNeedWorkorder(req.getNeedWorkorder());
        if (req.getStatus() != null) s.setStatus(req.getStatus());
        if (req.getDescription() != null) s.setDescription(req.getDescription());
        normalize(s);
        supplyMapper.updateById(s);
    }

    /** 名称、描述里不能有金融类违禁词（农资不记命中记录） */
    private static void checkWords(FarmSupplySaveReqVO req) {
        List<String> words = FarmComplianceService.findBannedWords(Arrays.asList(req.getName(), req.getDescription()));
        if (!words.isEmpty()) throw new FarmBannedWordsException(words);
    }

    /** 农药类：不能零售，必须走工单 */
    private static void normalize(FarmSupplyDO s) {
        if ("pesticide".equals(s.getCat())) {
            s.setRetailAllowed(false);
            s.setNeedWorkorder(true);
        }
    }
}
