package org.deepstack.ai.runtime;

import org.deepstack.ai.engine.api.WorkflowStreamEvent;
import org.deepstack.ai.runtime.spi.EmittedCard;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流流式输出辅助：向 SSE sink 安全写入事件，并构建执行期间的卡片发射器。
 * <p>
 * 职责边界：只做「事件往 sink 推」与「出卡回调的装配」——{@link #tryEmit} 是节点进度、
 * 出卡、工作流完成事件共用的底层写入原语（失败仅告警，不中断执行）；{@link #buildCardEmitter}
 * 负责补 threadId、登记 pending_cards、回填 CardPort。图编译与节点包装不在本类职责内。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowCardEmitter {

    private final AgentNodeContext agentNodeContext;

    /** 向流式 sink 推送事件；失败仅打 warn，不中断执行。 */
    public static void tryEmit(Sinks.Many<WorkflowStreamEvent> sink, WorkflowStreamEvent event) {
        if (sink == null) {
            return;
        }
        try {
            sink.tryEmitNext(event);
        } catch (Exception e) {
            log.warn("Failed to emit stream event: {}", e.getMessage());
        }
    }

    /**
     * 图执行期间的卡片发射器：补 threadId、记 pending、回填 CardPort。
     */
    public Consumer<Object> buildCardEmitter(String threadId, Sinks.Many<WorkflowStreamEvent> sink) {
        return card -> {
            try {
                EmittedCard emitted = EmittedCard.from(card);
                if (emitted != null && !StringUtils.hasText(emitted.getThreadId())) {
                    emitted.setThreadId(threadId);
                }
                Map<String, Object> meta = new LinkedHashMap<>();
                if (emitted != null) {
                    if (StringUtils.hasText(emitted.getCardId())) {
                        meta.put("cardId", emitted.getCardId());
                    }
                    if (StringUtils.hasText(emitted.getCardType())) {
                        meta.put("cardType", emitted.getCardType());
                    }
                    if (StringUtils.hasText(emitted.getThreadId())) {
                        meta.put("threadId", emitted.getThreadId());
                    }
                }
                if (!meta.isEmpty()) {
                    // 按 threadId 定位运行上下文：出卡回调可能在节点之外的线程上执行
                    RunContext runCtx = RunContext.forThread(threadId);
                    if (runCtx != null) {
                        runCtx.addPendingCard(meta);
                    }
                }
                if (sink != null) {
                    String cardJson = JSON.toJSONString(emitted != null ? emitted : card);
                    tryEmit(sink, WorkflowStreamEvent.card(null, null, cardJson));
                }
                agentNodeContext.getCardPort().afterEmitted(emitted);
            } catch (Exception e) {
                log.warn("Failed to emit card event: {}", e.getMessage());
            }
        };
    }
}
