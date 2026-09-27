package org.deepstack.ai.runtime.spi;

import org.deepstack.ai.engine.GraphRunRequest;
import org.deepstack.ai.engine.GraphRunResponse;

import java.util.Map;

/**
 * HITL 恢复端口：卡片 confirm/edit/reject 后继续图执行。
 */
public interface HitlPort {

    /**
     * 从 checkpoint 恢复图执行。
     *
     * @param agentCode    智能体编码（用于加载 graphDefinition）
     * @param threadId     检查点线程 ID
     * @param stateUpdates 写入状态的增量（card_action、modifiedPayload 等）
     * @param baseRequest  用户/会话等上下文（可空字段忽略）
     */
    GraphRunResponse resume(String agentCode, String threadId, Map<String, Object> stateUpdates,
                            GraphRunRequest baseRequest);

    default boolean available() {
        return true;
    }
}
