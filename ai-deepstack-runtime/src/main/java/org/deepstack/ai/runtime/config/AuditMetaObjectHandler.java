package org.deepstack.ai.runtime.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充：业务代码无需赋值 {@code isDel}/{@code createTime}/{@code updateTime}。
 * <ul>
 *   <li>INSERT：isDel=0，createTime=now，updateTime=now（DB 亦有 DEFAULT + UPDATE 触发器双保险）</li>
 *   <li>UPDATE：updateTime=now</li>
 * </ul>
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /**
     * INSERT 时填充 isDel / createTime / updateTime。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "isDel", Integer.class, 0);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * UPDATE 时刷新 updateTime。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 强制覆盖，避免业务侧残留旧值
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
