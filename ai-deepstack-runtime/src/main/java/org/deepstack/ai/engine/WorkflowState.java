package org.deepstack.ai.engine;


import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流共享状态。
 *
 */
public class WorkflowState extends AgentState {

    public static final String MESSAGES = "messages";
    public static final String RESPONSE = "response";
    public static final String INTENT = "intent";
    public static final String CONTEXT_VARS = "context_vars";
    public static final String NODE_EXECUTIONS = "node_executions";
    public static final String EXECUTION_CONFIG = "execution_config";
    public static final String CHAT_HISTORY = "chat_history";
    public static final String USER_ID = "userId";

    /**
     * 当前请求的会话 ID（由 ChatServiceImpl / WorkflowCompiler 注入）。
     * <p>
     * 写入 state 而非仅依赖 ThreadLocal，避免 LangGraph 异步线程丢失 request context，
     * 导致卡片落库 conversationId 为空、reject 无法反查 model。
     * </p>
     */
    public static final String CONVERSATION_ID = "conversationId";

    /**
     * 编排注入的工具编码列表（运行侧只按 code 解析回调）。
     */
    public static final String TOOL_CODES = "toolCodes";

    /**
     * 卡片流扩展：待确认卡片列表。
     * PlanDraftNode 产出卡片时写入此字段，AwaitConfirmNode 读取此字段决定是否挂起。
     */
    public static final String PENDING_CARDS = "pending_cards";

    /**
     * HITL 恢复时写入的用户动作码：见 {@link org.deepstack.ai.kernel.enums.card.CardActionTypeEnum}。
     */
    public static final String CARD_ACTION = "card_action";

    /**
     * HITL 恢复时编辑后的 payload（edit 动作）。
     */
    public static final String CARD_MODIFIED_PAYLOAD = "card_modified_payload";

    /**
     * Checkpoint 线程 ID（与 RunnableConfig.threadId 对齐，供节点/卡片读取）。
     */
    public static final String THREAD_ID = "threadId";

    /**
     * 卡片流扩展：已处理卡片历史。
     */
    public static final String CARD_HISTORY = "card_history";

    /**
     * 默认 State schema（含预定义字段）。
     */
    public static Map<String, Channel<?>> defaultSchema() {
        Map<String, Channel<?>> schema = new LinkedHashMap<>();
        schema.put(MESSAGES, Channels.appender(ArrayList::new));
        schema.put(RESPONSE, Channels.base(() -> ""));
        schema.put(INTENT, Channels.base(() -> ""));
        schema.put(CONTEXT_VARS, Channels.base(() -> new LinkedHashMap<String, Object>()));
        schema.put(NODE_EXECUTIONS, Channels.appender(ArrayList::new));
        schema.put(EXECUTION_CONFIG, Channels.base(() -> new LinkedHashMap<String, Object>()));
        schema.put(CHAT_HISTORY, Channels.base(() -> new ArrayList<Map<String, String>>()));
        schema.put(USER_ID, Channels.base(() -> ""));
        schema.put(CONVERSATION_ID, Channels.base(() -> ""));
        schema.put(TOOL_CODES, Channels.base(() -> new ArrayList<String>()));
        schema.put(PENDING_CARDS, Channels.base(() -> new ArrayList<Map<String, Object>>()));
        schema.put(CARD_ACTION, Channels.base(() -> ""));
        schema.put(CARD_MODIFIED_PAYLOAD, Channels.base(() -> new LinkedHashMap<String, Object>()));
        schema.put(THREAD_ID, Channels.base(() -> ""));
        schema.put(CARD_HISTORY, Channels.appender(ArrayList::new));
        return schema;
    }

    /**
     * @param initData 初始状态 map
     */
    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }
}
