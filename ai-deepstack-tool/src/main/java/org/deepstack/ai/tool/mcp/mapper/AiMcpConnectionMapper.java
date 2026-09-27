package org.deepstack.ai.tool.mcp.mapper;

import org.deepstack.ai.tool.mcp.model.entity.AiMcpConnection;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 连接表 {@code ai_mcp_connection} 的 MyBatis-Plus Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供通用 CRUD，无自定义 SQL。
 */
@Mapper
public interface AiMcpConnectionMapper extends BaseMapper<AiMcpConnection> {
}
