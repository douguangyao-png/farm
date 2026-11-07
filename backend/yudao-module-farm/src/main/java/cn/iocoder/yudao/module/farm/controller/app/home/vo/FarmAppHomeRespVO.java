package cn.iocoder.yudao.module.farm.controller.app.home.vo;

import cn.iocoder.yudao.module.farm.controller.admin.product.vo.FarmProductRespVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** C 端首页（mock：farm-app.ts 18–35 行） */
@Data
public class FarmAppHomeRespVO {

    private String tenantName;
    /** [{ image, title, url }] */
    private List<Map<String, Object>> banners;
    private List<Base> bases;
    private List<FarmProductRespVO> products;
    private List<Article> articles;
    private Boolean demo = true;

    @Data
    public static class Base {
        private Long id;
        private String name;
        private String cover;
        private String address;
        private Boolean live = true;
    }

    @Data
    public static class Article {
        private Long id;
        private String title;
        private String introduction;
        private String picUrl;
        private LocalDateTime createTime;
    }
}
