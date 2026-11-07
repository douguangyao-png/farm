package cn.iocoder.yudao.module.farm.service;

import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAdoptionDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmAssetDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmBaseDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmMemberDO;
import cn.iocoder.yudao.module.farm.dal.dataobject.FarmProductDO;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAdoptionMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmAssetMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmBaseMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmMemberMapper;
import cn.iocoder.yudao.module.farm.dal.mysql.FarmProductMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 列表/详情拼装用的批量查询：先收集 id，一次查出再在内存里拼，避免 N+1。
 * 对应 mock 的 farm/views.ts 里的 member() / asset() / product() / user() 等查找。
 */
@Component
public class FarmViewLookup {

    @Autowired
    private FarmMemberMapper memberMapper;
    @Autowired
    private FarmAssetMapper assetMapper;
    @Autowired
    private FarmProductMapper productMapper;
    @Autowired
    private FarmBaseMapper baseMapper;
    @Autowired
    private FarmAdoptionMapper adoptionMapper;
    @Autowired
    private AdminUserApi adminUserApi;

    /** 托管方式中文名（mock CARE_MODE_NAME） */
    public static String careModeName(String mode) {
        if ("full".equals(mode)) return "全托管";
        if ("semi".equals(mode)) return "半托管";
        if ("self".equals(mode)) return "不托管";
        return "";
    }

    public Map<Long, FarmMemberDO> members(Collection<Long> ids) {
        return load(ids, l -> memberMapper.selectList(FarmMemberDO::getId, l), FarmMemberDO::getId);
    }

    public Map<Long, FarmAssetDO> assets(Collection<Long> ids) {
        return load(ids, l -> assetMapper.selectList(FarmAssetDO::getId, l), FarmAssetDO::getId);
    }

    public Map<Long, FarmProductDO> products(Collection<Long> ids) {
        return load(ids, l -> productMapper.selectList(FarmProductDO::getId, l), FarmProductDO::getId);
    }

    public Map<Long, FarmBaseDO> bases(Collection<Long> ids) {
        return load(ids, l -> baseMapper.selectList(FarmBaseDO::getId, l), FarmBaseDO::getId);
    }

    public Map<Long, FarmAdoptionDO> adoptions(Collection<Long> ids) {
        return load(ids, l -> adoptionMapper.selectList(FarmAdoptionDO::getId, l), FarmAdoptionDO::getId);
    }

    /** 管家（芋道用户）昵称：id -> nickname */
    public Map<Long, String> userNames(Collection<Long> ids) {
        List<Long> clean = clean(ids);
        Map<Long, String> out = new HashMap<>();
        if (clean.isEmpty()) return out;
        List<AdminUserRespDTO> users = adminUserApi.getUserList(clean);
        if (users != null) {
            for (AdminUserRespDTO u : users) {
                out.put(u.getId(), u.getNickname() == null ? "" : u.getNickname());
            }
        }
        return out;
    }

    private static List<Long> clean(Collection<Long> ids) {
        if (ids == null) return new ArrayList<>();
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private static <T> Map<Long, T> load(Collection<Long> ids, Function<List<Long>, List<T>> loader, Function<T, Long> idFn) {
        List<Long> clean = clean(ids);
        if (clean.isEmpty()) return new HashMap<>();
        return loader.apply(clean).stream().collect(Collectors.toMap(idFn, Function.identity(), (a, b) -> a));
    }
}
