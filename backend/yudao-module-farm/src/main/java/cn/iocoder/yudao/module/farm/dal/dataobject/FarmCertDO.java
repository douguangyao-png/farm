package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** farm_cert（第二期 B；建表 SQL 见 docs/plans/第二期-登记/B.md） */
@TableName(value = "farm_cert", autoResultMap = true)
@KeySequence("farm_cert_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmCertDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String name;
    private String no;
    /** 必备 / 建议 / 按需 */
    private String certType;
    /** null = 长期有效 */
    private LocalDateTime validUntil;
    /** verified / pending / renew */
    private String verify;
    /** public / private */
    private String publicMode;
    private String fileUrl;
    /** 1 = 演示预置数据 */
    private Integer demo;
}
