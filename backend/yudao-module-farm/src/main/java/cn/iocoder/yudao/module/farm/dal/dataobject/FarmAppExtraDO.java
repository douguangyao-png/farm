package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * farm_app_extra：C 端第二期补页的会员小数据，一张表按 kind 区分（建表 SQL 见 docs/plans/第二期-登记/C端.md）。
 * kind：favorite（收藏，targetId=商品/基地编号，payload.type）、address（收货地址）、review（评价，targetId=商品）、
 * visit（预约到访，targetId=基地）、transfer（转赠，targetId=认养）、chat（管家聊天）、msg_read（消息已读，payload.key）。
 */
@TableName(value = "farm_app_extra", autoResultMap = true)
@KeySequence("farm_app_extra_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmAppExtraDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long memberId;
    private String kind;
    private Long targetId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;
}
