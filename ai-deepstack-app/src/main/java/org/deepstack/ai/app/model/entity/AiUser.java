package org.deepstack.ai.app.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用层登录用户（对应表 {@code ai_user}，JWT 鉴权主体）。
 */
@Data
@TableName("ai_user")
public class AiUser implements Serializable {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录名（唯一） */
    private String username;

    /** 密码哈希（BCrypt） */
    private String passwordHash;

    /** 显示名 */
    private String displayName;

    /** 是否启用：1 启用，0 停用 */
    private Integer enabled;

    /** 是否管理员：1是 0否 */
    private Integer isAdmin;

    /** 逻辑删除：0 正常，1 已删 */
    @TableLogic
    private Integer isDel;

    /** 创建时间（插入自动填充 / DB 默认，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新自动填充 / DB 触发器，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
