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
 * 知识库文档实体
 * <p>
 * 记录上传/录入/抓取的原始资料及解析、向量化状态。
 * </p>
 *
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument implements Serializable {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库 ID */
    private Long knowledgeBaseId;

    /** 文档标题 */
    private String title;

    /** 来源类型：见 {@link org.deepstack.ai.kernel.enums.knowledge.DocSourceTypeEnum} */
    private Integer sourceType;

    /** 来源 URL */
    private String sourceUrl;

    /** 对象存储 key */
    private String fileKey;

    /** 本地路径（可选） */
    private String filePath;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;

    /** 解析后的原始文本 */
    private String rawContent;

    /** 内容哈希（去重） */
    private String contentHash;

    /** 标签（逗号分隔） */
    private String tags;

    /** 解析状态码：见 DocParseStatusEnum */
    private Integer parseStatus;

    /** 向量化状态码：见 DocEmbedStatusEnum */
    private Integer embedStatus;

    /** 写图状态码：见 DocGraphStatusEnum */
    private Integer graphStatus;

    /** 写图失败原因 */
    private String graphError;

    /** 分块数量 */
    private Integer chunkCount;

    /** 失败错误信息 */
    private String errorMsg;

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
