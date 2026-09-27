package org.deepstack.ai.knowledge.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 知识库修改请求
 *
 * <p>禁止修改 {@code baseCode}（业务唯一标识）。
 * 修改 {@code embeddingModelCode} 必须先确保 KB 内无 chunk，否则向量维度对不齐。</p>
 *
 */
@Data
public class KnowledgeBaseUpdateRequest implements Serializable {

    @NotNull(message = "知识库 id 不能为空")
    private Long id;

    @Size(max = 128, message = "知识库名称长度不能超过 128")
    private String baseName;

    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    @Size(max = 32, message = "领域长度不能超过 32")
    private String domain;

    @Size(max = 64, message = "embedding 模型编码长度不能超过 64")
    private String embeddingModelCode;

    @Min(value = 100, message = "切片大小不能小于 100")
    @Max(value = 4000, message = "切片大小不能大于 4000")
    private Integer chunkSize;

    @Min(value = 0, message = "重叠数不能小于 0")
    @Max(value = 1000, message = "重叠数不能大于 1000")
    private Integer chunkOverlap;

    @Min(value = 1, message = "Top-K 不能小于 1")
    @Max(value = 50, message = "Top-K 不能大于 50")
    private Integer topK;

    private BigDecimal similarityThreshold;

    /** 是否启用图谱增强：1是 0否 */
    private Integer enableGraph;

    /** 库级图谱抽取 CHAT 模型编码 */
    @Size(max = 64, message = "graphModelCode 长度不能超过 64")
    private String graphModelCode;

    private Integer enabled;
}
