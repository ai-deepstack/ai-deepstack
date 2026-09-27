package org.deepstack.ai.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识库配置实体
 * <p>
 * 定义知识库元信息、分片策略、检索参数及 Embedding 模型。
 * </p>
 *
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 知识库编码（唯一） */
    private String baseCode;

    /** 知识库名称 */
    private String baseName;

    /** 描述 */
    private String description;

    /** 业务域标签 */
    private String domain;

    /** Embedding 模型编码（ai_model.model_code） */
    private String embeddingModelCode;

    /** 分块大小（字符/token 近似） */
    private Integer chunkSize;

    /** 分块重叠长度 */
    private Integer chunkOverlap;

    /** 检索返回条数 */
    private Integer topK;

    /** 相似度阈值 */
    private BigDecimal similarityThreshold;

    /** 是否启用图谱增强：1是 0否 */
    private Integer enableGraph;

    /** 库级图谱抽取 CHAT 模型（空则用 sys_config） */
    private String graphModelCode;

    /** 外部 RAG 数据集 ID（预留） */
    private String ragDatasetId;

    /** 是否启用：1启用 0停用 */
    private Integer enabled;

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
