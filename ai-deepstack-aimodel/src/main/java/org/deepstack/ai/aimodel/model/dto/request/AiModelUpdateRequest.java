package org.deepstack.ai.aimodel.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 模型修改请求（CHAT + EMBEDDING）
 *
 * <p>不含 apiKey 字段；修改 apiKey 走单独接口 {@link ApiKeyUpdateRequest}。</p>
 * <p>v1.2 由 {@code ChatModelUpdateRequest} 重命名而来，新增 {@code modelType / extraJson} 字段。</p>
 *
 */
@Data
public class AiModelUpdateRequest implements Serializable {

    /**
     * 模型主键
     */
    @NotNull(message = "模型 id 不能为空")
    private Long id;

    @Size(max = 64, message = "模型编码长度不能超过 64")
    private String modelCode;

    @Size(max = 128, message = "模型名称长度不能超过 128")
    private String modelName;

    /** 模型类型码：见 ModelTypeEnum */
    private Integer modelType;

    @Size(max = 32, message = "供应商长度不能超过 32")
    private String provider;

    @Size(max = 256, message = "API base url 长度不能超过 256")
    private String baseUrl;

    @Size(max = 128, message = "供应商模型参数名长度不能超过 128")
    private String apiModelName;

    @Size(max = 2048, message = "扩展配置长度不能超过 2048")
    private String extraJson;

    private Integer enabled;

    /** 可见性：0公共 1私有 */
    private Integer visibility;

    @Size(max = 512, message = "备注长度不能超过 512")
    private String remark;
}
