package cn.iocoder.yudao.module.farm.controller.app.home.vo;

import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductRespVO;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** C 端商品详情（mock：farm-app.ts 37–47 行）：商品视图 + 基地信息 + 托管对比表 */
@Data
public class FarmAppProductDetailRespVO extends FarmProductRespVO {

    /** { name, address, certNo }，商品没有基地时为 null */
    private Map<String, Object> base;
    /** [{ item, full, semi, self }]：每项对应各托管档位「您来做 / 农场做 / —」 */
    private List<Map<String, Object>> compareRows;
    /** [{ mode, items:[{id,name,cat,price,unit,recommended}] }]：每个已配置托管方式的农资套餐；full 为空数组 */
    private List<Map<String, Object>> supplyPackages;
}
