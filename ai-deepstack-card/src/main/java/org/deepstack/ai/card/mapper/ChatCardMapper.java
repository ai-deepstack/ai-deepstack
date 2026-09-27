package org.deepstack.ai.card.mapper;

import org.deepstack.ai.card.model.entity.ChatCardEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 对话卡片 Mapper
 */
@Mapper
public interface ChatCardMapper extends BaseMapper<ChatCardEntity> {

    /**
     * 乐观锁状态更新：仅当当前 status 匹配时才更新为新 status。
     *
     * @param cardId         业务 cardId
     * @param expectedStatus 期望的当前状态码
     * @param newStatus      要切换到的新状态码
     * @return 受影响行数（1 表示成功，0 表示状态已被其它请求改掉）
     */
    @Update("UPDATE ai_chat_card SET status = #{newStatus}, update_time = CURRENT_TIMESTAMP " +
            "WHERE card_id = #{cardId} AND status = #{expectedStatus} AND is_del = 0")
    int updateStatusIfMatch(@Param("cardId") String cardId,
                            @Param("expectedStatus") Integer expectedStatus,
                            @Param("newStatus") Integer newStatus);
}
