package org.deepstack.ai.kernel.model;

import lombok.Data;

import java.time.Instant;

/**
 * 请求级上下文（耗时起点等），存于 ThreadLocal。
 */
@Data
public class RequestInfo {
    private static final ThreadLocal<RequestInfo> REQUEST_INFO = new ThreadLocal<>();

    private Long startTime = Instant.now().toEpochMilli();
    private Long cost = 0L;

    /**
     * 获取当前请求信息；未绑定则返回新实例（不写入 ThreadLocal）。
     */
    public static RequestInfo get() {
        return REQUEST_INFO.get() == null ? new RequestInfo() : REQUEST_INFO.get();
    }

    /**
     * 初始化并绑定当前线程的 RequestInfo。
     */
    public static void init() {
        set(new RequestInfo());
    }

    /**
     * 绑定指定 RequestInfo 到当前线程。
     */
    public static void set(RequestInfo requestInfo) {
        REQUEST_INFO.set(requestInfo);
    }

    /**
     * 清除当前线程绑定，避免泄漏。
     */
    public static void remove() {
        REQUEST_INFO.remove();
    }
}
