package org.deepstack.ai.runtime;



import org.deepstack.ai.engine.WorkflowStreamEvent;

import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSession;

import reactor.core.publisher.Sinks;



import java.util.ArrayList;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.function.Consumer;



/**

 * 单次图执行的请求作用域：user/会话/线程、流 sink、出卡 emitter、pending_cards、工具披露会话。

 * <p>

 * 由 {@link WorkflowCompiler} 在 execute/resume/stream 入口 {@link #install}，结束时 {@link #clear}。

 * </p>

 */

public final class RunContext {



    private static final ThreadLocal<RunContext> HOLDER = new ThreadLocal<>();



    private final String userId;

    private final String conversationId;

    private final String agentCode;

    private final String threadId;



    private Sinks.Many<WorkflowStreamEvent> streamSink;

    private Consumer<Object> cardEmitter;

    private ToolDisclosureSession toolDisclosureSession;

    private final List<Map<String, Object>> pendingCards = new ArrayList<>();



    private RunContext(String userId, String conversationId, String agentCode, String threadId) {

        this.userId = userId;

        this.conversationId = conversationId;

        this.agentCode = agentCode;

        this.threadId = threadId;

    }



    /**

     * 安装当前线程的图执行上下文。

     *

     * @param userId         用户 ID

     * @param conversationId 会话 ID

     * @param agentCode      智能体编码

     * @param threadId       Checkpoint 线程 ID

     * @return 新安装的上下文实例

     */

    public static RunContext install(String userId, String conversationId, String agentCode, String threadId) {

        RunContext ctx = new RunContext(userId, conversationId, agentCode, threadId);

        HOLDER.set(ctx);

        return ctx;

    }



    /** 清除当前线程的图执行上下文。 */

    public static void clear() {

        HOLDER.remove();

    }



    /**

     * 获取当前线程的图执行上下文。

     *

     * @return 已安装上下文；未安装时为 null

     */

    public static RunContext current() {

        return HOLDER.get();

    }



    /**

     * 读取当前线程 ID（未安装上下文时返回 null）。

     *

     * @return threadId 或 null

     */

    public static String peekThreadId() {

        RunContext ctx = HOLDER.get();

        return ctx != null ? ctx.threadId : null;

    }



    /**

     * 记录本节点产出的待确认卡片元数据。

     *

     * @param cardMeta 卡片元信息（cardId、cardType、threadId 等）

     */

    public static void recordPendingCard(Map<String, Object> cardMeta) {

        RunContext ctx = HOLDER.get();

        if (ctx == null || cardMeta == null || cardMeta.isEmpty()) {

            return;

        }

        ctx.pendingCards.add(new LinkedHashMap<>(cardMeta));

    }



    /**

     * 取出并清空当前线程缓冲的 pending_cards。

     *

     * @return 卡片元数据列表；无缓冲时返回空列表

     */

    public static List<Map<String, Object>> drainPendingCards() {

        RunContext ctx = HOLDER.get();

        if (ctx == null || ctx.pendingCards.isEmpty()) {

            return List.of();

        }

        List<Map<String, Object>> copy = new ArrayList<>(ctx.pendingCards);

        ctx.pendingCards.clear();

        return copy;

    }



    /** @return 用户 ID */

    public String userId() {

        return userId;

    }



    /** @return 会话 ID */

    public String conversationId() {

        return conversationId;

    }



    /** @return 智能体编码 */

    public String agentCode() {

        return agentCode;

    }



    /** @return Checkpoint 线程 ID */

    public String threadId() {

        return threadId;

    }



    /** @return SSE 流 sink；未绑定时为 null */

    public Sinks.Many<WorkflowStreamEvent> streamSink() {

        return streamSink;

    }



    /**

     * 绑定 SSE 流 sink。

     *

     * @param streamSink 流式事件 sink

     */

    public void setStreamSink(Sinks.Many<WorkflowStreamEvent> streamSink) {

        this.streamSink = streamSink;

    }



    /** @return 卡片发射器；未绑定时为 null */

    public Consumer<Object> cardEmitter() {

        return cardEmitter;

    }



    /**

     * 绑定卡片发射器。

     *

     * @param cardEmitter 出卡回调

     */

    public void setCardEmitter(Consumer<Object> cardEmitter) {

        this.cardEmitter = cardEmitter;

    }



    /** @return 工具渐进披露会话；未打开时为 null */

    public ToolDisclosureSession toolDisclosureSession() {

        return toolDisclosureSession;

    }



    /**

     * 绑定工具渐进披露会话。

     *

     * @param toolDisclosureSession 当前请求的工具披露会话

     */

    public void setToolDisclosureSession(ToolDisclosureSession toolDisclosureSession) {

        this.toolDisclosureSession = toolDisclosureSession;

    }

}


