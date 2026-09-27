package org.deepstack.ai.app.settings.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 运营级系统配置（{@code deepstack.sys_config}）。
 */
@Data
@TableName("sys_config")
public class SysConfig implements Serializable {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键（唯一） */
    private String configKey;

    /** 配置名（展示用） */
    private String configName;

    private String configValue;

    /** string / yes_no / int / json / model */
    private String valueType;

    private String description;

    private LocalDateTime updatedAt;
}
