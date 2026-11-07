package cn.iocoder.yudao.module.farm.controller.admin.serviceorder.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 对应 mock serviceOrderView */
@Data
public class FarmServiceOrderRespVO {
    private Long id;
    private String no;
    private Long fromTaskId;
    private String taskTitle;
    private Long adoptionId;
    private Long assetId;
    private String assetCode;
    private Long memberId;
    private String memberName;
    private Long stewardId;
    private String stewardName;
    private String serviceType;
    /** 分 */
    private Long price;
    private Integer status;
    /** { steward, farm, platform }，百分比 */
    private Map<String, Object> split;
    private List<String> proofPhotos;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime acceptTime;
    private LocalDateTime finishTime;
    /** 认养人的托管方式中文名（全托管 / 半托管 / 不托管），无认养关系时为空串 */
    private String careModeName;
    /** 现场照片张数 = proofPhotos 的长度 */
    private Integer proofCount;
}
