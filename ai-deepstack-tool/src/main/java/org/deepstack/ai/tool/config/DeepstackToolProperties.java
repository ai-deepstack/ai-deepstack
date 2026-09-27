package org.deepstack.ai.tool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工具披露 / Index 相关配置。
 */
@Data
@ConfigurationProperties(prefix = "deepstack.tools")
public class DeepstackToolProperties {

    /**
     * off = 绑定工具全量塞给模型（默认）；progressive = 元工具 + search/load。
     */
    private String disclosureMode = "off";

    /**
     * progressive 模式下，允许集数量 ≤ 该值时仍全量披露（小 Agent 免 search）。
     * 0 = 不启用该捷径。
     */
    private int progressiveFullBelow = 5;

    /** progressive 模式下 tool_search 默认返回条数。 */
    private int searchTopK = 8;

    /**
     * 是否启用渐进式工具披露。
     *
     * @return disclosureMode 为 progressive 时 true
     */
    public boolean isProgressive() {
        return "progressive".equalsIgnoreCase(disclosureMode);
    }
}
