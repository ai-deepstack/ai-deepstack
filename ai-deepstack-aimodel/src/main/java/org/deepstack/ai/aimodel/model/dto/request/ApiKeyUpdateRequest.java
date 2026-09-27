package org.deepstack.ai.aimodel.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 模型 API Key 修改请求
 * <p>
 * 仅用于单独编辑 apiKey 字段。明文传输，控制台需走 HTTPS。
 * </p>
 *
 */
@Data
public class ApiKeyUpdateRequest implements Serializable {

    /**
     * 新的 API Key（明文）
     */
    @NotBlank(message = "API Key 不能为空")
    @Size(max = 512, message = "API Key 长度不能超过 512")
    private String apiKey;
}
