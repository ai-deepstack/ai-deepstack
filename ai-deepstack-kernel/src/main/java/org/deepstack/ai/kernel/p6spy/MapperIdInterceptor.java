package org.deepstack.ai.kernel.p6spy;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 在执行 SQL 前把 {@link MappedStatement#getId()} 写入 {@link MybatisSqlContext}，
 * 供 P6Spy Formatter 打印 Mapper 类与方法。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class
        })
})
public class MapperIdInterceptor implements Interceptor {

    /** 拦截调用并附加上下文。 */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof MappedStatement) {
            MappedStatement ms = (MappedStatement) args[0];
            MybatisSqlContext.push(ms.getId());
            try {
                return invocation.proceed();
            } finally {
                MybatisSqlContext.pop();
            }
        }
        return invocation.proceed();
    }
}
