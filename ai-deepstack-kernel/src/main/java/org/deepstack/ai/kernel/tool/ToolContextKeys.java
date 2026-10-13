package org.deepstack.ai.kernel.tool;

/**
 * Spring AI {@link org.springframework.ai.chat.model.ToolContext} 共享键常量。
 * <p>
 * 放在 kernel，避免 card 等模块为常量依赖整套 runtime。
 * </p>
 */
public final class ToolContextKeys {

    private ToolContextKeys() {
    }

    public static final String USER_ID = "userId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String CARD_EMITTER = "cardEmitter";
    public static final String MODEL_CODE = "modelCode";
    /**
     * 当前图运行的 checkpoint 线程 ID。
     * <p>
     * 工具需要给产出物（如确认卡）带上 threadId 时从这里取，
     * 而不是去读运行期的请求上下文——工具可能跑在别的线程上。
     * </p>
     */
    public static final String THREAD_ID = "threadId";
}
