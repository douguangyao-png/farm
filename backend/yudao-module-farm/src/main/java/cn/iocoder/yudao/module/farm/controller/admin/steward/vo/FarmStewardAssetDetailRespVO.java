package cn.iocoder.yudao.module.farm.controller.admin.steward.vo;

import cn.iocoder.yudao.module.farm.controller.admin.asset.vo.FarmAssetRespVO;
import cn.iocoder.yudao.module.farm.controller.admin.record.vo.FarmRecordRespVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 标的详情（mock farm-steward.ts 113–129 行） */
@Data
public class FarmStewardAssetDetailRespVO {
    private FarmAssetRespVO asset;
    /** 没有进行中的认养则 null */
    private Adoption adoption;
    /** 没有认养人则 null */
    private Member member;
    /** envView：current / series / sensorName / demo */
    private Map<String, Object> env;
    private List<FarmRecordRespVO> records;
    private SelfTaskWeek selfTaskWeek;

    @Data
    public static class Adoption {
        private Long id;
        private String careMode;
        private String careModeName;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private BigDecimal promisedJin;
        private BigDecimal harvestedJin;
    }

    @Data
    public static class Member {
        private String name;
        private String mobile;
    }

    @Data
    public static class SelfTaskWeek {
        private Integer total;
        private Integer done;
        private Integer entrusted;
        private Integer overdue;
    }
}
