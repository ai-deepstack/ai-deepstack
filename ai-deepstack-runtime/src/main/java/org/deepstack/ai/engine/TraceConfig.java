package org.deepstack.ai.engine;

import org.deepstack.ai.kernel.enums.agent.TraceModeEnum;
import org.deepstack.ai.kernel.enums.common.YesNo;

import java.util.Map;

/**
 * 工作流执行的轨迹（Trace）配置。
 * <p>
 * 优先级（均为 {@link TraceModeEnum} 数字码）：
 * <ol>
 *   <li>AiAgent DB：trace_mode / stream_progress</li>
 *   <li>图 JSON executionConfig.traceMode（Number）</li>
 * </ol>
 * 非法数字码直接抛错，不做兜底。
 */
public class TraceConfig {

    private final TraceModeEnum traceMode;
    private final boolean streamProgress;

    /** 默认 RECORD + 不推流式进度。 */
    public TraceConfig() {
        this(TraceModeEnum.RECORD, false);
    }

    /**
     * @param traceMode      轨迹模式；null 视为 RECORD
     * @param streamProgress 是否推送节点进度事件
     */
    public TraceConfig(TraceModeEnum traceMode, boolean streamProgress) {
        this.traceMode = traceMode != null ? traceMode : TraceModeEnum.RECORD;
        this.streamProgress = streamProgress;
    }

    /**
     * 合并智能体与图默认配置。
     *
     * @param sceneTraceMode  AiAgent.traceMode，可为 null
     * @param sceneStreamProg AiAgent.streamProgress（1/0），可为 null
     * @param workflowDefault 图 definition.executionConfig，可为 null
     * @return 解析后的 TraceConfig
     */
    public static TraceConfig resolve(Integer sceneTraceMode,
                                      Integer sceneStreamProg,
                                      Map<String, Object> workflowDefault) {
        TraceModeEnum mode = TraceModeEnum.RECORD;
        boolean streamProgress = false;

        if (workflowDefault != null) {
            Object tm = workflowDefault.get("traceMode");
            if (tm instanceof Number n) {
                mode = TraceModeEnum.ofRequired(n.intValue());
            }
            Object sp = workflowDefault.get("streamProgress");
            if (sp instanceof Boolean b) {
                streamProgress = b;
            } else if (sp instanceof Number n) {
                streamProgress = YesNo.isYes(n.intValue());
            }
        }

        if (sceneTraceMode != null) {
            mode = TraceModeEnum.ofRequired(sceneTraceMode);
        }
        if (sceneStreamProg != null) {
            streamProgress = YesNo.isYes(sceneStreamProg);
        }

        return new TraceConfig(mode, streamProgress);
    }

    /** @return 是否为 NONE（不记节点时间线） */
    public boolean isTraceNone() {
        return traceMode == TraceModeEnum.NONE;
    }

    /** @return 是否记录节点执行（非 NONE） */
    public boolean shouldRecord() {
        return !isTraceNone();
    }

    /** @return 是否推送 NODE_START / NODE_COMPLETE（STREAM 且开启 streamProgress） */
    public boolean shouldStream() {
        return traceMode == TraceModeEnum.STREAM && streamProgress;
    }

    /**
     * 写入 WorkflowState.execution_config 的快照。
     *
     * @return 含 traceMode 码与 streamProgress 的 map
     */
    public Map<String, Object> toStateConfig() {
        return Map.of(
                "traceMode", traceMode.getCode(),
                "streamProgress", streamProgress
        );
    }

    /**
     * 日志 / usage 用枚举名。
     *
     * @return 如 RECORD / STREAM / NONE
     */
    public String getTraceMode() {
        return traceMode.name();
    }

    /** @return 是否开启流式进度开关 */
    public boolean isStreamProgress() {
        return streamProgress;
    }

    /** toString。 */
    @Override
    public String toString() {
        return "TraceConfig{traceMode=" + traceMode + ", streamProgress=" + streamProgress + "}";
    }
}
