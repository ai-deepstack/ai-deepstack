package org.deepstack.ai.aimodel.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 模型管理响应（apiKey 返回脱敏值）
 *
 *
*
 */
@Data
public class AiModelResponse implements Serializable {

    private Long id;

    /**
     * 模型编码
     */
    private String modelCode;

    /**
     * 模型显示名称
     */
    private String modelName;

    /**
     * 模型类型码：见 ModelTypeEnum
     */
    private Integer modelType;

    /** 模型类型中文名 */
    private String modelTypeName;

    /**
     * 供应商
     */
    private String provider;

    /**
     * API base url
     */
    private String baseUrl;

    /**
     * API key（脱敏：sk-yo****key）
     */
    private String apiKey;

    /**
     * 供应商模型参数名
     */
    private String apiModelName;

    /**
     * 扩展配置 JSON：embedding 的 dimensions 等
     */
    private String extraJson;

    /**
     * 是否启用
     */
    private Integer enabled;

    /** 启用状态中文名 */
    private String enabledName;

    /** 可见性：0公共 1私有 */
    private Integer visibility;

    /** 可见性中文名 */
    private String visibilityName;

    /** 私有模型所有者用户 id */
    private Long ownerId;

    /**
     * 备注
     */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
