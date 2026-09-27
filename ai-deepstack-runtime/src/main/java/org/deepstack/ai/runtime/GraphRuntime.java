package org.deepstack.ai.runtime;



import org.deepstack.ai.engine.GraphRunRequest;

import org.deepstack.ai.engine.GraphRunResponse;

import org.deepstack.ai.engine.GraphSpec;

import org.deepstack.ai.engine.TraceConfig;

import org.deepstack.ai.engine.WorkflowStreamEvent;

import reactor.core.publisher.Flux;



import java.util.Map;

import java.util.concurrent.CompletableFuture;



/**

 * 图执行窄门面：编排 / HITL / 试跑只依赖本接口，不依赖 {@link WorkflowCompiler} 内部细节。

 */

public interface GraphRuntime {



    /**

     * 同步执行工作流（默认 RECORD 追踪）。

     *

     * @param workflow 工作流规格

     * @param req      执行请求

     * @return 执行结果

     */

    GraphRunResponse execute(GraphSpec workflow, GraphRunRequest req);



    /**

     * 同步执行工作流（显式 TraceConfig）。

     *

     * @param workflow    工作流规格

     * @param req         执行请求

     * @param traceConfig 追踪/推流配置

     * @return 执行结果

     */

    GraphRunResponse execute(GraphSpec workflow, GraphRunRequest req, TraceConfig traceConfig);



    /**

     * 从 Checkpoint 恢复执行（HITL resume）。

     *

     * @param workflow      工作流规格

     * @param threadId      Checkpoint 线程 ID

     * @param stateUpdates  resume 状态增量

     * @param baseRequest   基础请求（用户/会话等）

     * @param traceConfig   追踪配置

     * @return 恢复后的执行结果

     */

    GraphRunResponse resume(GraphSpec workflow, String threadId, Map<String, Object> stateUpdates,

                            GraphRunRequest baseRequest, TraceConfig traceConfig);



    /**

     * 流式执行工作流，推送节点进度与 LLM chunk 事件。

     *

     * @param workflow    工作流规格

     * @param req         执行请求

     * @param traceConfig 追踪配置（须启用 stream）

     * @param runId       可选 run ID，用于取消

     * @return 流式事件 Flux

     */

    Flux<WorkflowStreamEvent> executeStream(GraphSpec workflow, GraphRunRequest req,

                                            TraceConfig traceConfig, Long runId);



    /**

     * 解析 Checkpoint threadId。

     *

     * @param req 执行请求

     * @return threadId

     */

    String resolveThreadId(GraphRunRequest req);



    /**

     * 使指定工作流的编译缓存失效。

     *

     * @param workflowId 工作流 ID

     */

    void invalidateCache(Long workflowId);



    /**

     * 注册可取消的后台执行。

     *

     * @param runId  run ID

     * @param future 执行 Future

     */

    void registerRunningExecution(Long runId, CompletableFuture<Void> future);



    /**

     * 按 run ID 取消正在运行的执行。

     *

     * @param runId agent_workflow_run ID

     * @return 找到并取消则为 true

     */

    boolean cancelExecution(Long runId);

}


