package org.deepstack.ai.card.mapper;

import org.deepstack.ai.card.model.entity.ChatCardActionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话卡片动作历史 Mapper
 *
 */
@Mapper
public interface ChatCardActionMapper extends BaseMapper<ChatCardActionEntity> {
}
