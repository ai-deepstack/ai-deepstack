package org.deepstack.ai.agent.web.userprompt;

import org.deepstack.ai.agent.model.dto.userprompt.request.UserPromptPageRequest;
import org.deepstack.ai.agent.model.dto.userprompt.response.UserPromptResponse;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.agent.model.entity.userprompt.UserPrompt;
import org.deepstack.ai.agent.service.userprompt.UserPromptService;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户全域提示词管理 API（客户端，合规审计：只看和删）
 * <p>
 * 单体统一 API。
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/user-prompts")
@RequiredArgsConstructor
public class UserPromptController {

    private final UserPromptService userPromptService;

    /**
     * 分页查询用户全域提示词（合规审计）。
     *
     * @param req 分页与筛选条件
     * @return 提示词分页结果
     */
    @GetMapping("/page")
    public Response<PageInfo<UserPromptResponse>> page(UserPromptPageRequest req) {
        log.info("用户提示词分页: pageNum={}, pageSize={}",
                req != null ? req.getPageNum() : null,
                req != null ? req.getPageSize() : null);
        return Response.success(PageInfoUtils.of(userPromptService.page(req), this::toResponse));
    }

    /**
     * 按主键查询用户提示词详情。
     *
     * @param id 提示词记录 id
     * @return 详情，不存在时为 null
     */
    @GetMapping("/{id}")
    public Response<UserPromptResponse> get(@PathVariable("id") Long id) {
        log.info("查询用户提示词: id={}", id);
        UserPrompt entity = userPromptService.getById(id);
        return Response.success(entity != null ? toResponse(entity) : null);
    }

    /**
     * 删除用户提示词记录。
     *
     * @param id 提示词记录 id
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("删除用户提示词: id={}", id);
        userPromptService.removeById(id);
        return Response.success();
    }

    /** Entity → API Response。*/
    private UserPromptResponse toResponse(UserPrompt e) {
        UserPromptResponse r = new UserPromptResponse();
        r.setId(e.getId());
        r.setUserId(e.getUserId());
        r.setPromptContent(e.getPromptContent());
        r.setCreateTime(e.getCreateTime());
        r.setUpdateTime(e.getUpdateTime());
        return r;
    }
}
