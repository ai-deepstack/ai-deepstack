package org.deepstack.ai.knowledge.model.entity;

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
 * 知识库分块与向量实体
 * <p>
 * 向量默认 1024 维（pgvector）；{@code contentTsv} 为 DB 生成列，不映射入库。
 * </p>
 *
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库 ID */
    private Long knowledgeBaseId;

    /** 所属文档 ID */
    private Long documentId;

    /** 文档内分块序号（从 0 起） */
    private Integer chunkIndex;

    /** 分块文本 */
    private String content;

    /** 估算 token 数 */
    private Integer tokenCount;

    /** 向量（pgvector，默认 1024 维；Java 侧用 String 承载） */
    private String embedding;

    /** 分块元数据 JSON */
    private String metadata;

    // ===== 公共字段 =====

    /** 逻辑删除：0正常 1已删（插入自动填充 0，业务勿赋值） */
    @TableLogic
    private Integer isDel;

    /** 创建时间（插入自动填充 / DB 默认，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入/更新自动填充 / DB 触发器，业务勿赋值） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 全文检索 tsvector（生成列，exist=false） */
    @TableField(exist = false)
    private String contentTsv;
}
