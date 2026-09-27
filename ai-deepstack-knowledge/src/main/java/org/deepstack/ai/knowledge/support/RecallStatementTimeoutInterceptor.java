package org.deepstack.ai.knowledge.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Statement;

/**
 * 为当前线程打过标的召回 SQL 设置 {@link Statement#setQueryTimeout(int)}。
 * <p>
 * 其它请求不设置 ThreadLocal，行为与原来一致。
 * </p>
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare",
                args = {Connection.class, Integer.class})
})
public class RecallStatementTimeoutInterceptor implements Interceptor {

    /** 拦截调用并附加上下文。 */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Statement statement = (Statement) invocation.proceed();
        Integer seconds = RecallQueryTimeout.seconds();
        if (seconds != null && seconds > 0) {
            statement.setQueryTimeout(seconds);
            log.debug("召回 SQL queryTimeout={}s", seconds);
        }
        return statement;
    }

    /** 包装目标对象。 */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
