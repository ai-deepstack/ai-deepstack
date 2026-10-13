package org.deepstack.ai.runtime;

import org.deepstack.ai.engine.api.WorkflowStreamEvent;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSession;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * 单次图执行的请求作用域：user/会话/线程、流 sink、出卡 emitter、pending_cards、工具披露会话。
 * <p>
 * 由 {@link WorkflowCompiler} 在 execute/resume/stream 入口 {@link #install}，结束时 {@link #clear}。
 * 节点可能被 LangGraph 调度到其它线程执行，ThreadLocal 在那些线程上取不到，因此除 ThreadLocal 外
 * 还按 threadId 建索引，异步节点用 {@link #currentOrThread(String)} 取回上下文。
 * </p>
 */
public final class RunContext {

    private static final ThreadLocal<RunContext> HOLDER = new ThreadLocal<>();
    /** threadId → 上下文；节点在异步线程执行时按 state 里的 threadId 取回。 */
    private static final ConcurrentHashMap<String, RunContext> BY_THREAD = new ConcurrentHashMap<>();

    private final String userId;
    private final String conversationId;
    private final String agentCode;
    private final String threadId;

    private Sinks.Many<WorkflowStreamEvent> streamSink;
    private Consumer<Object> cardEmitter;
    private ToolDisclosureSession toolDisclosureSession;
    /** 出卡在节点线程、汇总在回调线程，用并发队列避免丢卡与竞态。 */
    private final ConcurrentLinkedQueue<Map<String, Object>> pendingCards = new ConcurrentLinkedQueue<>();

    private RunContext(String userId, String conversationId, String agentCode, String threadId) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.agentCode = agentCode;
        this.threadId = threadId;
    }

    /**
     * 安装当前线程的图执行上下文，并登记 threadId 索引。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param agentCode      Agent 编码
     * @param threadId       Checkpoint 线程 ID
     * @return 新安装的上下文实例
     */
    public static RunContext install(String userId, String conversationId, String agentCode, String threadId) {
        // 同一线程重复 install（例如宿主复用工作线程）时先摘掉上一份索引，避免旧 threadId 残留
        RunContext previous = HOLDER.get();
        if (previous != null && previous.threadId != null) {
            BY_THREAD.remove(previous.threadId, previous);
        }
        RunContext ctx = new RunContext(userId, conversationId, agentCode, threadId);
        HOLDER.set(ctx);
        if (threadId != null && !threadId.isBlank()) {
            BY_THREAD.put(threadId, ctx);
        }
        return ctx;
    }

    /**
     * 把指定上下文绑定到当前线程执行 {@code body}，结束后（含抛异常）还原原绑定。
     * <p>
     * 图节点会被 LangGraph 调度到安装上下文之外的线程上；节点包装器用本方法把请求上下文
     * 带过去，使节点内 {@code RunContext.current()}（如卡片落库取 threadId）始终拿得到值。
     * 传 null 或与当前绑定相同时不做任何改动。
     * </p>
     *
     * @param ctx  期望的上下文，可为 null
     * @param body 执行体
     * @param <T>  返回值类型
     * @return body 的返回值
     */
    public static <T> T bound(RunContext ctx, java.util.function.Supplier<T> body) {
        RunContext previous = HOLDER.get();
        boolean switched = ctx != null && ctx != previous;
        if (switched) {
            HOLDER.set(ctx);
        }
        try {
            return body.get();
        } finally {
            if (switched) {
                if (previous != null) {
                    HOLDER.set(previous);
                } else {
                    HOLDER.remove();
                }
            }
        }
    }

    /** 清除当前线程的图执行上下文，并注销 threadId 索引。 */
    public static void clear() {
        RunContext ctx = HOLDER.get();
        if (ctx != null && ctx.threadId != null) {
            BY_THREAD.remove(ctx.threadId, ctx);
        }
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
     * 按 checkpoint threadId 取上下文（不依赖当前线程）。
     *
     * @param threadId checkpoint 线程 ID
     * @return 上下文；无对应执行时为 null
     */
    public static RunContext forThread(String threadId) {
        return threadId != null && !threadId.isBlank() ? BY_THREAD.get(threadId) : null;
    }

    /**
     * 先取当前线程上下文，取不到再按 threadId 解析。
     *
     * @param threadId checkpoint 线程 ID
     * @return 上下文；两者都取不到时为 null
     */
    public static RunContext currentOrThread(String threadId) {
        RunContext ctx = HOLDER.get();
        return ctx != null ? ctx : forThread(threadId);
    }

    /**
     * 读取当前线程的 threadId（未安装上下文时返回 null）。
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
    public void addPendingCard(Map<String, Object> cardMeta) {
        if (cardMeta != null && !cardMeta.isEmpty()) {
            pendingCards.add(new LinkedHashMap<>(cardMeta));
        }
    }

    /**
     * 取出并清空缓冲的 pending_cards。
     *
     * @return 卡片元数据列表；无缓冲时返回空列表
     */
    public List<Map<String, Object>> takePendingCards() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> item;
        while ((item = pendingCards.poll()) != null) {
            out.add(item);
        }
        return out;
    }

    /** @return 用户 ID */
    public String userId() {
        return userId;
    }

    /** @return 会话 ID */
    public String conversationId() {
        return conversationId;
    }

    /** @return Agent 编码 */
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
