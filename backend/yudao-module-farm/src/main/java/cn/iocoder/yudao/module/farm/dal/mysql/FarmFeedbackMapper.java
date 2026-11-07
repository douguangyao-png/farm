package cn.iocoder.yudao.module.farm.dal.mysql;

import cn.iocoder.yudao.module.farm.dal.dataobject.FarmFeedbackDO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface FarmFeedbackMapper extends BaseMapperX<FarmFeedbackDO> {

    /** 只查编号列（截图是大字段，不能整行取）。调用方负责用 TenantUtils.executeIgnore 查全部租户 */
    default List<String> selectAllNo() {
        return selectList(new LambdaQueryWrapper<FarmFeedbackDO>().select(FarmFeedbackDO::getNo))
                .stream().map(FarmFeedbackDO::getNo).collect(Collectors.toList());
    }
}
