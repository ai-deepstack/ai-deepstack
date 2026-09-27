package org.deepstack.ai.knowledge.model.dto.request;


import org.deepstack.ai.kernel.enums.common.YesNo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 知识库新建请求
 *
 */
@Data
public class KnowledgeBaseCreateRequest implements Serializable {

    @NotBlank(message = "知识库编码不能为空")
    @Size(max = 64, message = "知识库编码长度不能超过 64")
    private String baseCode;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称长度不能超过 128")
    private String baseName;

    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    @Size(max = 32, message = "领域长度不能超过 32")
    private String domain;

    /**
     * 关联 ai_model.model_code（必须 model_type=EMBEDDING）
     */
    @NotBlank(message = "embedding 模型编码不能为空")
    @Size(max = 64, message = "embedding 模型编码长度不能超过 64")
    private String embeddingModelCode;

    /**
     * 切片 token 数（默认 800）
     */
    @Min(value = 100, message = "切片大小不能小于 100")
    @Max(value = 4000, message = "切片大小不能大于 4000")
    private Integer chunkSize = 800;

    /**
     * 切片重叠 token 数（默认 200）
     */
    @Min(value = 0, message = "重叠数不能小于 0")
    @Max(value = 1000, message = "重叠数不能大于 1000")
    private Integer chunkOverlap = 200;

    /**
     * 默认检索 Top-K（默认 5）
     */
    @Min(value = 1, message = "Top-K 不能小于 1")
    @Max(value = 50, message = "Top-K 不能大于 50")
    private Integer topK = 5;

    /**
     * 相似度阈值（默认 0.700）
     */
    private BigDecimal similarityThreshold = new BigDecimal("0.700");

    /** 是否启用图谱增强：1是 0否 */
    private Integer enableGraph = YesNo.NO.getCode();

    /** 库级图谱抽取 CHAT 模型编码（空则用 sys_config / 平台默认） */
    @Size(max = 64, message = "graphModelCode 长度不能超过 64")
    private String graphModelCode;

    private Integer enabled = YesNo.YES.getCode();
}
