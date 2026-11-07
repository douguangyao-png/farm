package cn.iocoder.yudao.module.farm.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;


/** farm_article（字段见 backend/sql/postgresql/farm.sql；由 gen.py 生成） */
@TableName(value = "farm_article", autoResultMap = true)
@KeySequence("farm_article_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class FarmArticleDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long categoryId;
    private String title;
    private String author;
    private String picUrl;
    private String introduction;
    private String content;
    private Integer sort;
    private Integer status;
    private Integer browseCount;
    private Boolean recommendHot;
    private Boolean recommendBanner;
    private Long spuId;
}
