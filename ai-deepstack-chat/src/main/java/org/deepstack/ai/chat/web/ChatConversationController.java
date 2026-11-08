package org.deepstack.ai.chat.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deepstack.ai.chat.model.dto.request.ChatConversationPageRequest;
import org.deepstack.ai.chat.model.dto.request.ChatConversationRenameRequest;
import org.deepstack.ai.chat.model.dto.response.ChatConversationResponse;
import org.deepstack.ai.chat.model.dto.response.ChatMessageResponse;
import org.deepstack.ai.chat.model.entity.ChatConversation;
import org.deepstack.ai.chat.model.entity.memory.ChatMemoryRecord;
import org.deepstack.ai.chat.service.ChatConversationService;
import org.deepstack.ai.chat.service.memory.ChatMemoryService;
import org.deepstack.ai.kernel.auth.LoginUser;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话历史 API（客户端）
 * <p>仅返回当前登录用户自己的会话与消息。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatConversationController {

    private final ChatConversationService chatConversationService;
    private final ChatMemoryService chatMemoryService;

    /**
     * 分页查询当前用户的会话历史（按最后消息时间倒序）。
     *
     * @param req       分页参数
     * @param loginUser 当前登录用户
     * @return 会话分页结果
     */
    @GetMapping("/page")
    public Response<PageInfo<ChatConversationResponse>> page(ChatConversationPageRequest req,
                                                             @LoginUser AppUserInfo loginUser) {
        String userId = requireUserId(loginUser);
        int pageNum = req != null && req.getPageNum() != null ? req.getPageNum() : 1;
        int pageSize = req != null && req.getPageSize() != null ? req.getPageSize() : 20;
        String agentCode = req != null ? req.getAgentCode() : null;
        log.info("会话历史分页: userId={}, agentCode={}, pageNum={}, pageSize={}",
                userId, agentCode, pageNum, pageSize);
        IPage<ChatConversation> page = chatConversationService.pageByUserId(userId, agentCode, pageNum, pageSize);
        return Response.success(PageInfoUtils.of(page, this::toResponse));
    }

    /**
     * 按会话拉取历史消息（user / assistant），用于恢复对话窗口。
     *
     * @param conversationId 业务会话 ID
     * @param loginUser      当前登录用户
     * @return 按时间升序的消息列表
     */
    @GetMapping("/{conversationId}/messages")
    public Response<List<ChatMessageResponse>> messages(@PathVariable("conversationId") String conversationId,
                                                        @LoginUser AppUserInfo loginUser) {
        String userId = requireUserId(loginUser);
        chatConversationService.requireOwned(conversationId, userId);
        log.info("会话历史消息: conversationId={}, userId={}", conversationId, userId);
        List<ChatMessageResponse> messages = chatMemoryService.listMessagesForClient(conversationId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
        return Response.success(messages);
    }

    /**
     * 重命名会话。
     *
     * @param conversationId 业务会话 ID
     * @param req            新标题
     * @param loginUser      当前登录用户
     */
    @PutMapping("/{conversationId}")
    public Response<Void> rename(@PathVariable("conversationId") String conversationId,
                                 @RequestBody ChatConversationRenameRequest req,
                                 @LoginUser AppUserInfo loginUser) {
        String userId = requireUserId(loginUser);
        log.info("重命名会话: conversationId={}, userId={}", conversationId, userId);
        chatConversationService.renameOwned(conversationId, userId, req != null ? req.getTitle() : null);
        return Response.success();
    }

    /**
     * 删除会话及其历史消息。
     *
     * @param conversationId 业务会话 ID
     * @param loginUser      当前登录用户
     */
    @DeleteMapping("/{conversationId}")
    public Response<Void> delete(@PathVariable("conversationId") String conversationId,
                                 @LoginUser AppUserInfo loginUser) {
        String userId = requireUserId(loginUser);
        log.info("删除会话: conversationId={}, userId={}", conversationId, userId);
        chatConversationService.deleteOwned(conversationId, userId);
        return Response.success();
    }

    /** 当前登录用户 ID；未登录直接拒绝。 */
    private static String requireUserId(AppUserInfo loginUser) {
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(CommonErrorCode.NEED_LOGIN);
        }
        return String.valueOf(loginUser.getUserId());
    }

    /** Entity → 列表项。 */
    private ChatConversationResponse toResponse(ChatConversation c) {
        ChatConversationResponse r = new ChatConversationResponse();
        r.setConversationId(c.getConversationId());
        r.setTitle(c.getTitle());
        r.setAgentCode(c.getAgentCode());
        r.setMessageCount(c.getMessageCount());
        r.setLastMessageAt(c.getLastMessageAt());
        r.setCreateTime(c.getCreateTime());
        return r;
    }

    /** 记忆记录 → 前端消息。 */
    private ChatMessageResponse toMessageResponse(ChatMemoryRecord m) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.setRole(m.getMessageType());
        r.setContent(m.getContent());
        r.setCreateTime(m.getCreateTime());
        return r;
    }
}
