package org.deepstack.ai.tool.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 工具管理响应
 * <p>
 * 包含 {@code javaFallbackDescription} 字段，用于前端只读展示 Java {@code @Tool} 注解的 description。
 * 该字段由后端反射读取，前端不可编辑。
 * </p>
 *
 */
@Data
public class AiToolResponse implements Serializable {

    private Long id;

    /**
     * 工具编码
     */
    private String toolCode;

    /**
     * 工具显示名称
     */
    private String toolName;

    /**
     * 给 LLM 的工具描述（DB 字段，前端可编辑；为空时运行时用 javaFallbackDescription 兜底）
     */
    private String description;

    /**
     * Java @Tool 注解的 description（只读，前端展示用，不可编辑）
     * <p>
     * 由后端通过反射从 handler_bean 对应的 Bean 类的 @Tool 方法上读取。
     * 前端展示逻辑：description 非空时显示 description，为空时显示 javaFallbackDescription。
     * </p>
     */
    private String javaFallbackDescription;

    /**
     * Spring 容器中 ToolHandler Bean 的名称
     */
    private String handlerBean;

    /** 来源类型码：见 ToolSourceTypeEnum */
    private Integer sourceType;

    /** 来源类型中文名 */
    private String sourceTypeName;

    private String mcpConnectionCode;

    private String mcpToolName;

    /**
     * 是否启用
     */
    private Integer enabled;

    /** 启用状态中文名 */
    private String enabledName;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
