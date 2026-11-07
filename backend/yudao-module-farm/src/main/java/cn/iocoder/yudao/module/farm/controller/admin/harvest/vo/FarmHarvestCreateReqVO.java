package cn.iocoder.yudao.module.farm.controller.admin.harvest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FarmHarvestCreateReqVO {
    private Long adoptionId;
    private String crop;
    private BigDecimal weightJin;
    /** 采收日期，毫秒时间戳；不传按今天 */
    private Long harvestDate;
    private List<String> photos;
    private String remark;
    /** 农事类型（播种 / 施肥 / 病虫害防治 / 浇水 / 搭架整枝 / 采摘采收 / 翻耕），不传按「采摘采收」；写进生长档案内容 */
    private String farmType;
    /** 收获明细（多品类）。传了就以它为准，crop / weightJin 忽略；crop 存各品类名用「、」连接，weightJin 存合计 */
    private List<Item> items;

    @Data
    public static class Item {
        private String crop;
        private BigDecimal weightJin;
    }
}
