package org.deepstack.ai.agent.service.userprompt;

import org.deepstack.ai.agent.model.dto.userprompt.request.UserPromptPageRequest;
import org.deepstack.ai.agent.model.entity.userprompt.UserPrompt;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户级全局提示词服务
 * <p>
 * 管理每个用户的个性化提示词，在所有会话中生效。
 * 基于 DB 持久化 + Redis 缓存，服务重启后不丢失。
 * </p>
 *
 */
public interface UserPromptService extends IService<UserPrompt> {

    // ===== 运行时查询 =====

    /**
     * 获取用户的全局提示词
     *
     * @param userId 用户ID
     * @return 用户全局提示词，不存在时返回 null
     */
    String getUserPrompt(String userId);

    /**
     * 设置用户的全局提示词
     *
     * @param userId 用户ID
     * @param prompt 提示词内容
     */
    void setUserPrompt(String userId, String prompt);

    /**
     * 删除用户的全局提示词
     *
     * @param userId 用户ID
     */
    void removeUserPrompt(String userId);

    // ===== CRUD =====

    /**
     * 分页查询用户提示词
     */
    IPage<UserPrompt> page(UserPromptPageRequest req);
}
