package org.deepstack.ai.aimodel.model.dto.request;


import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.model.ModelVisibilityEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 模型新建请求（CHAT + EMBEDDING）
 *
 * <p>v1.2 由 {@code ChatModelCreateRequest} 重命名而来，新增 {@code modelType / extraJson} 字段。</p>
 *
 */
@Data
public class AiModelCreateRequest implements Serializable {

    /**
     * 模型编码（唯一业务标识）
     */
    @NotBlank(message = "模型编码不能为空")
    @Size(max = 64, message = "模型编码长度不能超过 64")
    private String modelCode;

    /**
     * 模型显示名称
     */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 128, message = "模型名称长度不能超过 128")
    private String modelName;

    /**
     * 模型类型码：0=对话 1=向量（见 ModelTypeEnum）
     */
    @NotNull(message = "模型类型不能为空")
    private Integer modelType;

    /**
     * 供应商：deepseek / qwen / openai / zhipu / moonshot
     */
    @NotBlank(message = "供应商不能为空")
    @Size(max = 32, message = "供应商长度不能超过 32")
    private String provider;

    /**
     * API base url
     */
    @NotBlank(message = "API base url 不能为空")
    @Size(max = 256, message = "API base url 长度不能超过 256")
    private String baseUrl;

    /**
     * API key（明文，仅创建时使用）
     */
    @NotBlank(message = "API Key 不能为空")
    @Size(max = 512, message = "API Key 长度不能超过 512")
    private String apiKey;

    /**
     * 传给供应商的 model 参数
     */
    @NotBlank(message = "供应商模型参数名不能为空")
    @Size(max = 128, message = "供应商模型参数名长度不能超过 128")
    private String apiModelName;

    /**
     * 扩展配置 JSON：embedding 的 dimensions、provider 特殊参数等
     */
    @Size(max = 2048, message = "扩展配置长度不能超过 2048")
    private String extraJson;

    /**
     * 是否启用，默认 true
     */
    private Integer enabled = YesNo.YES.getCode();

    /**
     * 可见性：见 ModelVisibilityEnum；默认公共
     */
    private Integer visibility = ModelVisibilityEnum.PUBLIC.getCode();

    /**
     * 备注
     */
    @Size(max = 512, message = "备注长度不能超过 512")
    private String remark;
}
