package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** farm_ship_batch 配送批次（第一期补齐；建表见 backend/sql/postgresql/farm-p1b.sql） */
@TableName("farm_ship_batch")
@KeySequence("farm_ship_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmShipBatchDO extends TenantBaseDO {
    @TableId
    private Long id;
    /** PC20261108001 */
    private String no;
    private Long harvestId;
    private LocalDate planDate;
    /** 0 待发 / 10 已发出 / 20 已完成 */
    private Integer status;
    private String remark;
}
