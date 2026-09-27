package org.deepstack.ai.tool.mcp.model.dto.request;

import lombok.Data;

/**
 * MCP 连接分页查询请求。
 */
@Data
public class McpConnectionPageRequest {

    /** 页码，从 1 开始，默认 1 */
    private long pageNum = 1;

    /** 每页条数，默认 20 */
    private long pageSize = 20;

    /** 启用状态筛选：1=启用，0=停用；为空不过滤 */
    private Integer enabled;

    /** 关键字：匹配连接编码或名称（模糊） */
    private String keyword;
}
