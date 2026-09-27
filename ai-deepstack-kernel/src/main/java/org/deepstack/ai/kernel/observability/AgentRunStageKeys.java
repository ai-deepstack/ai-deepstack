package org.deepstack.ai.kernel.observability;

/**
 * {@code agent_workflow_run.stages} JSON 字段名约定（CHAT 阶段计时等）。
 */
public final class AgentRunStageKeys {

    /** 用户画像加载耗时（毫秒）。 */
    public static final String USER_PROMPT_MS = "userPromptMs";
    /** RAG 召回耗时（毫秒）。 */
    public static final String RAG_MS = "ragMs";
    /** 长期记忆召回耗时（毫秒）。 */
    public static final String MEMORY_MS = "memoryMs";
    /** 拼装 system prompt 总耗时（毫秒）。 */
    public static final String PROMPT_MS = "promptMs";
    /** LLM 调用耗时（毫秒）。 */
    public static final String LLM_MS = "llmMs";
    /** 首 token 延迟（毫秒，流式）。 */
    public static final String FIRST_TOKEN_MS = "firstTokenMs";
    /** 拼 prompt 是否超时。 */
    public static final String PROMPT_TIMEOUT = "promptTimeout";
    /** 是否注入了用户画像。 */
    public static final String HAS_USER_PROMPT = "hasUserPrompt";
    /** 是否注入了 RAG。 */
    public static final String HAS_RAG = "hasRag";
    /** 是否注入了长期记忆。 */
    public static final String HAS_MEMORY = "hasMemory";

    /** 禁止实例化。 */
    private AgentRunStageKeys() {
    }
}
