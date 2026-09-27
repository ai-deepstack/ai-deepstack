package org.deepstack.ai.kernel.observability;

/**
 * Micrometer Tracing 写入 MDC 的标准键（与 Brave / micrometer-tracing 默认一致）。
 */
public final class MdcKeys {

    /** 链路追踪 ID。 */
    public static final String TRACE_ID = "traceId";
    /** 当前 span ID。 */
    public static final String SPAN_ID = "spanId";

    /** 禁止实例化。 */
    private MdcKeys() {
    }
}
