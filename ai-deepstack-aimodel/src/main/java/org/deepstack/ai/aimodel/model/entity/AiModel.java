package org.deepstack.ai.aimodel.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 模型配置实体（CHAT / EMBEDDING）
 * <p>
 * 每条记录代表一个可调用的模型实例（供应商 + base-url + api-key + 模型名）。
 * 多供应商共用 OpenAI 兼容协议，运行时按 id 动态构建客户端：
 * <ul>
 *   <li>{@code modelType=CHAT}      → {@code OpenAiChatModel}（由 AiChatClientFactory 构建）</li>
 *   <li>{@code modelType=EMBEDDING} → {@code OpenAiEmbeddingModel}（由 EmbeddingClientFactory 构建）</li>
 * </ul>
 * </p>
 *
 * <p>历史名称：原 {@code ChatModel}（@TableName("chat_model")）于 v1.2 重命名为 AiModel，
 * 表名同步迁移为 {@code ai_model}，新增 {@code model_type} 与 {@code extra_json} 列。</p>
 *
 */
@Data
@TableName("ai_model")
public class AiModel implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模型编码（唯一） */
    private String modelCode;

    /** 模型显示名称 */
    private String modelName;

    /** 模型类型：见 {@link org.deepstack.ai.kernel.enums.model.ModelTypeEnum} */
    private Integer modelType;

    /** 厂商标识，如 openai / dashscope */
    private String provider;

    /** OpenAI 兼容 API Base URL */
    private String baseUrl;

    /** API 密钥（敏感） */
    private String apiKey;

    /** 上游实际模型名，如 gpt-4o-mini */
    private String apiModelName;

    /** 扩展 JSON，如 {"dimensions":1024} */
    private String extraJson;

    /** 是否启用：1启用 0停用 */
    private Integer enabled;

    /** 可见性：见 {@link org.deepstack.ai.kernel.enums.model.ModelVisibilityEnum} */
    private Integer visibility;

    /** 私有模型所有者（AiUser.id） */
    private Long ownerId;

    /** 备注 */
    private String remark;

    // ===== 公共字段 =====

    /** 逻辑删除：0正常 1已删（插入自动填充 0，业务勿赋值） */
    @TableLogic
    private Integer isDel;

    /** 创建人 ID */
    private Long creatorId;

    /** 创建人名称 */
    private String creator;

    /** 修改人 ID */
    private Long modifierId;

    /** 修改人名称 */
    private String modifier;

        /** 创建时间（插入自动填充 / DB 默认，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

        /** 更新时间（插入/更新自动填充 / DB 触发器，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
