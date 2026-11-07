package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;


/** farm_article_category（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_article_category", autoResultMap = true)
@KeySequence("farm_article_category_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmArticleCategoryDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String name;
    private String picUrl;
    private Integer sort;
    private Integer status;
}
