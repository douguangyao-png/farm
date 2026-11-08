package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** farm_livestock_batch 畜禽批次（第一期补齐；建表见 backend/sql/postgresql/farm-p1b.sql） */
@TableName("farm_livestock_batch")
@KeySequence("farm_livestock_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmLivestockBatchDO extends TenantBaseDO {
    @TableId
    private Long id;
    /** XB20261108001 */
    private String no;
    private Long baseId;
    private String species;
    private String breed;
    private LocalDate inDate;
    private Integer count;
    private Integer outCount;
    /** 0 在栏 / 10 出栏中 / 20 已出栏 */
    private Integer status;
    private String pen;
    private String remark;
}
