package org.deepstack.ai.chat.mapper.memory;

import org.deepstack.ai.chat.model.entity.memory.ChatMemoryRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话记忆 Mapper
 *
 */
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryRecord> {

    /**
     * 单条 SQL 批量插入对话记忆记录。
     * 使用 PostgreSQL 多值 INSERT 以提高效率。
     *
     * @param records 待插入的记录
     * @return 受影响的行数
     */
    @Insert({
            "<script>",
            "INSERT INTO chat_memory (conversation_id, message_type, content, seq) VALUES",
            "<foreach collection='records' item='r' separator=','>",
            "(#{r.conversationId}, #{r.messageType}, #{r.content}, #{r.seq})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("records") List<ChatMemoryRecord> records);
}
