package cn.iocoder.yudao.module.farm.controller.admin.fulfill;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 履约类列表页（收成、配送、农资订单、农资商品、代管工单）的分页返回：{ list, total, stats }。
 * stats 是页面顶部统计卡 / 页签数量用的，字段见接口契约 2.7～2.9；mock 对应 farm/stats.ts。
 * 这几个页面的统计要看全租户数据，所以列表也在内存里过滤后再分页（演示数据量小，足够）。
 */
@Data
public class FarmFulfillPage<T> {
    private List<T> list;
    private Long total;
    private Map<String, Object> stats;

    public FarmFulfillPage() {
    }

    public FarmFulfillPage(List<T> all, PageParam req, Map<String, Object> stats) {
        this.total = (long) all.size();
        this.list = slice(all, req);
        this.stats = stats;
    }

    /** 按 pageNo / pageSize 截取；pageSize 为 -1（不分页）时返回全部 */
    public static <T> List<T> slice(List<T> all, PageParam req) {
        int size = req.getPageSize() == null ? 10 : req.getPageSize();
        if (size <= 0) return new ArrayList<>(all);
        int no = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int from = (int) Math.min((long) (no - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    /** 保留 1 位小数 */
    public static double r1(double v) {
        return Math.round(v * 10d) / 10d;
    }

    /** 有序 map 小工具：m("a", 1, "b", 2) */
    public static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }
}
