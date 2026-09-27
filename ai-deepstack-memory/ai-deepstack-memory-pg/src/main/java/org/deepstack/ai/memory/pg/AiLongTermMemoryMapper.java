package org.deepstack.ai.memory.pg;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiLongTermMemoryMapper extends BaseMapper<AiLongTermMemory> {

    @Select("""
            SELECT * FROM ai_long_term_memory
            WHERE is_del = 0
              AND user_id = #{userId}
              AND (#{agentCode} IS NULL OR #{agentCode} = '' OR agent_code = #{agentCode})
              AND (#{q} IS NULL OR #{q} = '' OR content ILIKE CONCAT('%', #{q}, '%'))
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<AiLongTermMemory> search(@Param("userId") String userId,
                                  @Param("agentCode") String agentCode,
                                  @Param("q") String q,
                                  @Param("limit") int limit);
}
