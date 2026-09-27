package org.deepstack.ai.agent.mapper.userprompt;

import org.deepstack.ai.agent.model.entity.userprompt.UserPrompt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户全域提示词 Mapper
 *
 */
@Mapper
public interface UserPromptMapper extends BaseMapper<UserPrompt> {
}
