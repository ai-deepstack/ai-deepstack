package org.deepstack.ai.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.deepstack.ai.app.model.entity.AiUser;

/**
 * 应用登录用户 Mapper（表 {@code ai_user}）。
 */
@Mapper
public interface AiUserMapper extends BaseMapper<AiUser> {
}
