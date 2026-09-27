package org.deepstack.ai.tool.mcp.service;

import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionCreateRequest;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionPageRequest;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionUpdateRequest;
import org.deepstack.ai.tool.mcp.model.dto.response.McpConnectionResponse;
import org.deepstack.ai.tool.mcp.model.entity.AiMcpConnection;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * MCP Server 连接的业务服务接口。
 * <p>
 * 负责连接的 CRUD、启停、密钥更新，以及「建连 + 同步远端工具到 {@code ai_tool}」。
 */
public interface AiMcpConnectionService extends IService<AiMcpConnection> {

    /**
     * 分页查询 MCP 连接。
     *
     * @param req 分页与筛选条件（关键字、启用状态等）
     * @return 连接实体分页结果
     */
    IPage<AiMcpConnection> page(McpConnectionPageRequest req);

    /**
     * 按主键查询连接详情（响应 DTO，不含密钥明文）。
     *
     * @param id 连接主键
     * @return 详情；不存在时返回 {@code null}
     */
    McpConnectionResponse getDetail(Long id);

    /**
     * 新建 MCP 连接。
     *
     * @param req 创建请求
     * @return 新建记录主键
     * @throws IllegalArgumentException 参数非法或连接编码已存在
     */
    Long create(McpConnectionCreateRequest req);

    /**
     * 更新 MCP 连接配置（不含密钥；密钥走 {@link #updateSecret}）。
     * <p>
     * 配置变更后会踢掉内存中的旧 Client。
     *
     * @param req 更新请求
     * @throws IllegalArgumentException 参数非法或连接不存在
     */
    void update(McpConnectionUpdateRequest req);

    /**
     * 单独更新连接密钥；空值表示清空。
     * <p>
     * 更新后会踢掉旧 Client，避免继续使用旧凭证。
     *
     * @param id     连接主键
     * @param secret 新密钥；空白则清空
     * @throws IllegalArgumentException 连接不存在
     */
    void updateSecret(Long id, String secret);

    /**
     * 启用连接（仅改库表状态，不立刻建连）。
     *
     * @param id 连接主键
     * @throws IllegalArgumentException 连接不存在
     */
    void enable(Long id);

    /**
     * 停用连接：改状态、移除 Client / Bridge 缓存，并刷新工具索引。
     *
     * @param id 连接主键
     * @throws IllegalArgumentException 连接不存在
     */
    void disable(Long id);

    /**
     * 删除连接：停用其下 MCP 工具、清理内存 Client，并刷新索引。
     *
     * @param id 连接主键
     * @throws IllegalArgumentException 连接不存在
     */
    void delete(Long id);

    /**
     * 连接（或重建）Client 并同步远端工具到 {@code ai_tool}。
     *
     * @param id 连接主键
     * @return 本次 upsert 的工具条数（新建 + 更新）
     * @throws IllegalStateException    MCP 总开关关闭，或连接已停用，或建连/同步失败
     * @throws IllegalArgumentException 连接不存在
     */
    int connectAndSync(Long id);

    /**
     * 实体转响应 DTO（含工具数、是否已配置密钥等派生字段）。
     *
     * @param entity 连接实体
     * @return 对外响应对象
     */
    McpConnectionResponse toResponse(AiMcpConnection entity);
}
