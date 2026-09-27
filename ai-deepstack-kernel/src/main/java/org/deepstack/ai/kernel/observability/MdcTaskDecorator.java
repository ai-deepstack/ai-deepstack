package org.deepstack.ai.kernel.observability;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 把提交线程的 MDC 拷到池线程。
 * <p>
 * {@code traceId}/{@code spanId} 由 Micrometer Tracing 在请求线程写入；
 * 线程池不会自动继承，故在提交时复制，结束后恢复，避免串请求。
 * </p>
 */
public class MdcTaskDecorator implements TaskDecorator {

    /**
     * 装饰任务：进入时设置提交线程 MDC，退出时恢复池线程原 MDC。
     *
     * @param runnable 原任务
     * @return 带 MDC 传播的 Runnable
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context != null) {
                MDC.setContextMap(context);
            } else {
                MDC.clear();
            }
            try {
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
