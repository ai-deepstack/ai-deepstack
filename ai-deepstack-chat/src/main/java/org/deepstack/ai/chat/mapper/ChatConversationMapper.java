package org.deepstack.ai.chat.mapper;

import org.deepstack.ai.chat.model.entity.ChatConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper
 *
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
