package org.deepstack.ai.agent.service.userprompt.impl;

import org.deepstack.ai.agent.model.dto.userprompt.request.UserPromptPageRequest;
import org.deepstack.ai.agent.model.entity.userprompt.UserPrompt;
import org.deepstack.ai.agent.mapper.userprompt.UserPromptMapper;
import org.deepstack.ai.agent.service.userprompt.UserPromptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户级全局提示词服务 —— 数据库实现
 * <p>
 * 基于 DB 持久化 + Spring Cache（Redis）缓存，变更后通过 evict 立即失效。
 * </p>
 *
 */
@Slf4j
@Service
public class DbUserPromptServiceImpl extends ServiceImpl<UserPromptMapper, UserPrompt> implements UserPromptService {

    // ===== 运行时查询 =====

    /**
     * 获取用户全域提示词（带缓存；不在日志输出全文）。
     *
     * @param userId 用户 ID
     * @return 提示词内容；未配置返回 null
     */
    @Override
    @Cacheable(value = "user_prompt", key = "#userId", unless = "#result == null")
    public String getUserPrompt(String userId) {
        log.info("getUserPrompt: userId={}", userId);
        UserPrompt entity = baseMapper.selectOne(
                new LambdaQueryWrapper<UserPrompt>()
                        .eq(UserPrompt::getUserId, userId)
        );
        if (entity == null) {
            log.warn("用户提示词未配置: userId={}", userId);
            return null;
        }
        return entity.getPromptContent();
    }

    /**
     * 设置用户全域提示词（日志仅记录长度，不输出内容）。
     *
     * @param userId 用户 ID
     * @param prompt 提示词内容
     */
    @Override
    @CacheEvict(value = "user_prompt", key = "#userId")
    public void setUserPrompt(String userId, String prompt) {
        log.info("Set user prompt: userId={}, length={}", userId, prompt != null ? prompt.length() : 0);

        UserPrompt existing = baseMapper.selectOne(
                new LambdaQueryWrapper<UserPrompt>()
                        .eq(UserPrompt::getUserId, userId)
        );

        if (existing != null) {
            existing.setPromptContent(prompt);
            baseMapper.updateById(existing);
        } else {
            UserPrompt entity = new UserPrompt();
            entity.setUserId(userId);
            entity.setPromptContent(prompt);
            baseMapper.insert(entity);
        }
    }

    /**
     * 删除用户全域提示词。
     *
     * @param userId 用户 ID
     */
    @Override
    @CacheEvict(value = "user_prompt", key = "#userId")
    public void removeUserPrompt(String userId) {
        log.info("Remove user prompt: userId={}", userId);
        baseMapper.delete(
                new LambdaQueryWrapper<UserPrompt>()
                        .eq(UserPrompt::getUserId, userId)
        );
    }

    // ===== CRUD =====

    /**
     * 分页查询用户提示词。
     *
     * @param req 分页与筛选条件
     * @return 分页结果
     */
    @Override
    public IPage<UserPrompt> page(UserPromptPageRequest req) {
        log.info("page: pageNum={}, pageSize={}, userId={}",
                req.getPageNum(), req.getPageSize(), req.getUserId());
        LambdaQueryWrapper<UserPrompt> wrapper = new LambdaQueryWrapper<UserPrompt>()
                .like(StringUtils.hasText(req.getUserId()), UserPrompt::getUserId, req.getUserId())
                .orderByDesc(UserPrompt::getUpdateTime);
        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * 按主键删除用户提示词并清空全部相关缓存。
     *
     * @param id 主键
     * @return 是否删除成功
     */
    @Override
    @CacheEvict(value = "user_prompt", allEntries = true)
    public boolean removeById(java.io.Serializable id) {
        log.info("removeById: id={}", id);
        boolean ok = super.removeById(id);
        log.info("Deleted user prompt: id={}, ok={}", id, ok);
        return ok;
    }
}
