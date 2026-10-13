package org.deepstack.ai.engine.tool;

/**
 * Agent 工具接口（插件化扩展）
 * <p>
 * 新增工具只需实现此接口 + 加 @Component，前端自动发现。
 * </p>
 *
 */
public interface AgentTool {

    /**
     * 工具编码（唯一标识）
     */
    String getCode();

    /**
     * 工具显示名称
     */
    String getName();

    /**
     * 工具描述（供 LLM 理解用途）
     */
    String getDescription();

    /**
     * 参数 JSON Schema
     * <p>
     * 示例：{"type":"object","properties":{"height":{"type":"number","description":"身高(cm)"},"weight":{"type":"number","description":"体重(kg)"}}, "required":["height","weight"]}
     * </p>
     */
    String getParametersSchema();

    /**
     * 执行工具
     *
     * @param params 参数（从工作流 state 中解析）
     * @return 执行结果文本
     */
    String execute(java.util.Map<String, Object> params);
}
