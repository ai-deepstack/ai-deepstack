package org.deepstack.ai.tool.mcp.web;

import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionCreateRequest;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionPageRequest;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionUpdateRequest;
import org.deepstack.ai.tool.mcp.model.dto.request.McpSecretUpdateRequest;
import org.deepstack.ai.tool.mcp.model.dto.response.McpConnectionResponse;
import org.deepstack.ai.tool.mcp.service.AiMcpConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MCP Server 连接管理。
 * <p>
 * 工具清单写入 {@code ai_tool}（source_type=MCP）；Agent 绑定工具编码，不绑定连接。
 * 密钥接口单独更新，列表/详情不回传明文。
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/connections")
@RequiredArgsConstructor
public class McpConnectionController {

    private final AiMcpConnectionService mcpConnectionService;

    /**
     * 分页查询 MCP 连接。
     *
     * @param req 分页与筛选条件
     * @return 统一分页响应
     */
    @GetMapping("/page")
    public Response<PageInfo<McpConnectionResponse>> page(McpConnectionPageRequest req) {
        log.info("API 分页查询 MCP 连接: pageNum={}, pageSize={}, enabled={}, keyword={}",
                req != null ? req.getPageNum() : null,
                req != null ? req.getPageSize() : null,
                req != null ? req.getEnabled() : null,
                req != null ? req.getKeyword() : null);
        return Response.success(PageInfoUtils.of(mcpConnectionService.page(req), mcpConnectionService::toResponse));
    }

    /**
     * 按主键查询连接详情。
     *
     * @param id 连接主键
     * @return 详情；不存在时 data 为 null
     */
    @GetMapping("/{id}")
    public Response<McpConnectionResponse> get(@PathVariable("id") Long id) {
        log.info("API 查询 MCP 连接详情: id={}", id);
        return Response.success(mcpConnectionService.getDetail(id));
    }

    /**
     * 新建 MCP 连接。
     *
     * @param req 创建请求
     * @return 空成功响应
     */
    @PostMapping
    public Response<Void> create(@RequestBody McpConnectionCreateRequest req) {
        log.info("API create MCP connection: code={}, transport={}",
                req != null ? req.getConnectionCode() : null,
                req != null ? req.getTransport() : null);
        mcpConnectionService.create(req);
        return Response.success();
    }

    /**
     * 更新 MCP 连接配置（不含密钥）。
     *
     * @param req 更新请求
     * @return 空成功响应
     */
    @PutMapping
    public Response<Void> update(@RequestBody McpConnectionUpdateRequest req) {
        log.info("API update MCP connection: id={}", req != null ? req.getId() : null);
        mcpConnectionService.update(req);
        return Response.success();
    }

    /**
     * 单独更新连接密钥。
     *
     * @param id  连接主键
     * @param req 密钥请求体
     * @return 空成功响应
     */
    @PutMapping("/{id}/secret")
    public Response<Void> updateSecret(@PathVariable("id") Long id, @RequestBody McpSecretUpdateRequest req) {
        log.info("API update MCP secret: id={}", id);
        mcpConnectionService.updateSecret(id, req != null ? req.getSecret() : null);
        return Response.success();
    }

    /**
     * 启用连接。
     *
     * @param id 连接主键
     * @return 空成功响应
     */
    @PutMapping("/{id}/enable")
    public Response<Void> enable(@PathVariable("id") Long id) {
        log.info("API enable MCP connection: id={}", id);
        mcpConnectionService.enable(id);
        return Response.success();
    }

    /**
     * 停用连接。
     *
     * @param id 连接主键
     * @return 空成功响应
     */
    @PutMapping("/{id}/disable")
    public Response<Void> disable(@PathVariable("id") Long id) {
        log.info("API disable MCP connection: id={}", id);
        mcpConnectionService.disable(id);
        return Response.success();
    }

    /**
     * 建连并同步远端工具到 {@code ai_tool}。
     *
     * @param id 连接主键
     * @return {@code upserted} 为本次 upsert 工具条数
     */
    @PostMapping("/{id}/sync")
    public Response<Map<String, Object>> sync(@PathVariable("id") Long id) {
        log.info("API sync MCP connection: id={}", id);
        int upserted = mcpConnectionService.connectAndSync(id);
        log.info("API sync MCP connection done: id={}, upserted={}", id, upserted);
        return Response.success(Map.of("upserted", upserted));
    }

    /**
     * 删除连接（并停用其下 MCP 工具）。
     *
     * @param id 连接主键
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("API delete MCP connection: id={}", id);
        mcpConnectionService.delete(id);
        return Response.success();
    }
}
