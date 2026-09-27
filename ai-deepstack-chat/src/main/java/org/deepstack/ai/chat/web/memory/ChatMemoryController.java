package org.deepstack.ai.chat.web.memory;

import org.deepstack.ai.chat.model.dto.memory.request.ChatMemoryStatRequest;
import org.deepstack.ai.chat.model.dto.memory.response.ChatMemoryStatResponse;
import org.deepstack.ai.chat.service.memory.ChatMemoryService;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话记忆统计管理 API（客户端，只统计不查内容）
 * <p>
 * 单体统一 API。
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/chat-memory")
@RequiredArgsConstructor
public class ChatMemoryController {

    private final ChatMemoryService chatMemoryService;

    /**
     * 查询对话记忆统计信息。
     *
     * @param req 统计条件（用户、会话等）
     * @return 记忆条数等统计结果
     */
    @GetMapping("/stat")
    public Response<ChatMemoryStatResponse> stat(ChatMemoryStatRequest req) {
        log.info("查询对话记忆统计: startDate={}, endDate={}",
                req != null ? req.getStartDate() : null,
                req != null ? req.getEndDate() : null);
        return Response.success(chatMemoryService.stat(req));
    }

    /**
     * 按会话 id 删除该会话的全部记忆。
     *
     * @param conversationId 会话 id
     */
    @DeleteMapping("/conversation/{conversationId}")
    public Response<Void> deleteConversation(@PathVariable("conversationId") String conversationId) {
        log.info("删除会话记忆: conversationId={}", conversationId);
        chatMemoryService.deleteByConversationId(conversationId);
        return Response.success();
    }

    /**
     * 删除指定日期之前的记忆。
     *
     * @param date 截止日期（该日之前的记录将被删除）
     */
    @DeleteMapping("/before")
    public Response<Void> deleteBefore(@RequestParam("date") String date) {
        log.info("按日期清理记忆: before={}", date);
        chatMemoryService.deleteBefore(date);
        return Response.success();
    }
}
