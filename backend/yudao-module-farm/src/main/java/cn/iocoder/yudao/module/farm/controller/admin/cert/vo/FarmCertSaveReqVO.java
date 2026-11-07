package cn.iocoder.yudao.module.farm.controller.admin.cert.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

/** 上传证照 / 改公示设置（第二期 B） */
@Data
public class FarmCertSaveReqVO {
    private Long id;
    private String name;
    private String no;
    /** 必备 / 建议 / 按需 */
    private String type;
    /** 毫秒时间戳，null = 长期有效 */
    private Long validUntil;
    /** public / private */
    private String publicMode;
    private String fileUrl;
}
