package org.deepstack.ai.tool.mcp.service.impl;

import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.tool.McpTransportEnum;
import org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.tool.index.ToolIndexHydrator;
import org.deepstack.ai.tool.mcp.McpClientRegistry;
import org.deepstack.ai.tool.mcp.McpToolBridge;
import org.deepstack.ai.tool.mcp.McpToolSyncService;
import org.deepstack.ai.tool.mcp.mapper.AiMcpConnectionMapper;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionCreateRequest;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionPageRequest;
import org.deepstack.ai.tool.mcp.model.dto.request.McpConnectionUpdateRequest;
import org.deepstack.ai.tool.mcp.model.dto.response.McpConnectionResponse;
import org.deepstack.ai.tool.mcp.model.entity.AiMcpConnection;
import org.deepstack.ai.tool.mcp.service.AiMcpConnectionService;
import org.deepstack.ai.tool.model.entity.AiTool;
import org.deepstack.ai.tool.service.AiToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * {@link AiMcpConnectionService} 实现：连接 CRUD、启停、密钥，以及建连同步。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiMcpConnectionServiceImpl extends ServiceImpl<AiMcpConnectionMapper, AiMcpConnection>
        implements AiMcpConnectionService {

    private final SysConfigPort sysConfigPort;
    private final McpClientRegistry mcpClientRegistry;
    private final McpToolBridge mcpToolBridge;
    private final McpToolSyncService mcpToolSyncService;
    private final AiToolService aiToolService;
    private final ToolPort toolPort;
    private final ToolIndexHydrator toolIndexHydrator;

    /**
     * 分页查询 MCP 连接。
     *
     * @param req 分页与筛选条件
     * @return 连接实体分页结果
     */
    @Override
    public IPage<AiMcpConnection> page(McpConnectionPageRequest req) {
        log.info("MCP page: keyword={}, enabled={}, pageNum={}, pageSize={}",
                req != null ? req.getKeyword() : null,
                req != null ? req.getEnabled() : null,
                req != null ? req.getPageNum() : null,
                req != null ? req.getPageSize() : null);
        // 按启用状态筛选，关键字匹配编码或名称
        LambdaQueryWrapper<AiMcpConnection> wrapper = new LambdaQueryWrapper<AiMcpConnection>()
                .eq(req.getEnabled() != null, AiMcpConnection::getEnabled, req.getEnabled())
                .orderByDesc(AiMcpConnection::getUpdateTime);
        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(AiMcpConnection::getConnectionCode, req.getKeyword())
                    .or()
                    .like(AiMcpConnection::getConnectionName, req.getKeyword()));
        }
        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * 按主键查询连接详情。
     *
     * @param id 连接主键
     * @return 详情 DTO；不存在时 null
     */
    @Override
    public McpConnectionResponse getDetail(Long id) {
        log.info("MCP getDetail: id={}", id);
        AiMcpConnection entity = baseMapper.selectById(id);
        if (entity == null) {
            log.warn("MCP 连接未找到: id={}", id);
        }
        return entity == null ? null : toResponse(entity);
    }

    /**
     * 新建 MCP 连接。
     *
     * @param req 创建请求
     * @return 新建记录主键
     */
    @Override
    @Transactional
    public Long create(McpConnectionCreateRequest req) {
        log.info("MCP create: code={}, transport={}",
                req != null ? req.getConnectionCode() : null,
                req != null ? req.getTransport() : null);
        if (req == null || !StringUtils.hasText(req.getConnectionCode())) {
            throw new IllegalArgumentException("connectionCode 不能为空");
        }
        String code = req.getConnectionCode().trim();
        // 编码唯一性校验
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<AiMcpConnection>()
                .eq(AiMcpConnection::getConnectionCode, code));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("连接编码已存在: " + code);
        }
        validateTransport(req.getTransport(), req.getEndpoint(), req.getCommand());

        AiMcpConnection entity = new AiMcpConnection();
        entity.setConnectionCode(code);
        entity.setConnectionName(StringUtils.hasText(req.getConnectionName())
                ? req.getConnectionName().trim()
                : code);
        entity.setTransport(req.getTransport());
        entity.setEndpoint(blankToNull(req.getEndpoint()));
        entity.setCommand(blankToNull(req.getCommand()));
        entity.setArgsJson(blankToNull(req.getArgsJson()));
        entity.setSecret(blankToNull(req.getSecret()));
        entity.setHeadersJson(blankToNull(req.getHeadersJson()));
        entity.setRequestTimeoutMs(req.getRequestTimeoutMs() != null ? req.getRequestTimeoutMs() : 30_000);
        entity.setEnabled(req.getEnabled() != null ? req.getEnabled() : YesNo.YES.getCode());
        baseMapper.insert(entity);
        log.info("Created MCP connection: id={}, code={}", entity.getId(), code);
        return entity.getId();
    }

    /**
     * 更新 MCP 连接配置（不含密钥）。
     *
     * @param req 更新请求
     */
    @Override
    @Transactional
    public void update(McpConnectionUpdateRequest req) {
        log.info("MCP update: id={}", req != null ? req.getId() : null);
        if (req == null || req.getId() == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        AiMcpConnection entity = baseMapper.selectById(req.getId());
        if (entity == null) {
            throw new IllegalArgumentException("连接不存在: id=" + req.getId());
        }
        // 仅覆盖请求中非 null 的字段
        if (req.getConnectionName() != null) {
            entity.setConnectionName(req.getConnectionName());
        }
        if (req.getTransport() != null) {
            entity.setTransport(req.getTransport());
        }
        if (req.getEndpoint() != null) {
            entity.setEndpoint(blankToNull(req.getEndpoint()));
        }
        if (req.getCommand() != null) {
            entity.setCommand(blankToNull(req.getCommand()));
        }
        if (req.getArgsJson() != null) {
            entity.setArgsJson(blankToNull(req.getArgsJson()));
        }
        if (req.getHeadersJson() != null) {
            entity.setHeadersJson(blankToNull(req.getHeadersJson()));
        }
        if (req.getRequestTimeoutMs() != null) {
            entity.setRequestTimeoutMs(req.getRequestTimeoutMs());
        }
        if (req.getEnabled() != null) {
            entity.setEnabled(req.getEnabled());
        }
        validateTransport(entity.getTransport(), entity.getEndpoint(), entity.getCommand());
        baseMapper.updateById(entity);
        // 配置变更后踢掉旧 Client，下次 sync 再重建
        mcpClientRegistry.remove(entity.getConnectionCode());
        mcpToolBridge.evictConnection(entity.getConnectionCode());
        log.info("Updated MCP connection: id={}, code={}, transport={}",
                req.getId(), entity.getConnectionCode(), entity.getTransport());
    }

    /**
     * 单独更新连接密钥。
     *
     * @param id     连接主键
     * @param secret 新密钥；空白则清空
     */
    @Override
    @Transactional
    public void updateSecret(Long id, String secret) {
        log.info("MCP updateSecret: id={}", id);
        AiMcpConnection entity = require(id);
        boolean clearing = !StringUtils.hasText(secret);
        entity.setSecret(blankToNull(secret));
        baseMapper.updateById(entity);
        // 密钥变更后必须重建 Client
        mcpClientRegistry.remove(entity.getConnectionCode());
        mcpToolBridge.evictConnection(entity.getConnectionCode());
        log.info("Updated MCP connection secret: id={}, code={}, cleared={}",
                id, entity.getConnectionCode(), clearing);
    }

    /**
     * 启用连接。
     *
     * @param id 连接主键
     */
    @Override
    @Transactional
    public void enable(Long id) {
        log.info("MCP enable: id={}", id);
        AiMcpConnection entity = require(id);
        entity.setEnabled(YesNo.YES.getCode());
        baseMapper.updateById(entity);
        log.info("Enabled MCP connection: id={}, code={}", id, entity.getConnectionCode());
    }

    /**
     * 停用连接并清理运行时缓存。
     *
     * @param id 连接主键
     */
    @Override
    @Transactional
    public void disable(Long id) {
        log.info("MCP disable: id={}", id);
        AiMcpConnection entity = require(id);
        entity.setEnabled(YesNo.NO.getCode());
        baseMapper.updateById(entity);
        // 停用后清理运行时 Client / Bridge，并刷新工具解析缓存与索引
        mcpClientRegistry.remove(entity.getConnectionCode());
        mcpToolBridge.evictConnection(entity.getConnectionCode());
        toolPort.evictAll();
        toolIndexHydrator.refreshAll();
        log.info("Disabled MCP connection: id={}, code={}", id, entity.getConnectionCode());
    }

    /**
     * 删除连接并停用其下 MCP 工具。
     *
     * @param id 连接主键
     */
    @Override
    @Transactional
    public void delete(Long id) {
        log.info("MCP delete: id={}", id);
        AiMcpConnection entity = require(id);
        String code = entity.getConnectionCode();
        // 停用该连接下 MCP 工具，保留绑定关系与历史行
        int disabledTools = 0;
        for (AiTool t : aiToolService.list(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getSourceType, ToolSourceTypeEnum.MCP.getCode())
                .eq(AiTool::getMcpConnectionCode, code))) {
            if (t.getEnabled() == null || t.getEnabled() == YesNo.YES.getCode()) {
                t.setEnabled(YesNo.NO.getCode());
                aiToolService.updateById(t);
                disabledTools++;
            }
        }
        mcpClientRegistry.remove(code);
        mcpToolBridge.evictConnection(code);
        baseMapper.deleteById(id);
        toolPort.evictAll();
        toolIndexHydrator.refreshAll();
        log.info("Deleted MCP connection: id={}, code={}, disabledTools={}", id, code, disabledTools);
    }

    /**
     * 建连并同步远端工具到 {@code ai_tool}。
     *
     * @param id 连接主键
     * @return 本次 upsert 工具条数
     */
    @Override
    public int connectAndSync(Long id) {
        if (!sysConfigPort.isYes(SysConfigKeys.MCP_ENABLED)) {
            log.warn("connectAndSync rejected: mcp.enabled=0, id={}", id);
            throw new IllegalStateException("mcp.enabled=0, cannot connect MCP");
        }
        AiMcpConnection entity = require(id);
        if (entity.getEnabled() == null || entity.getEnabled() != YesNo.YES.getCode()) {
            log.warn("connectAndSync rejected: connection disabled id={}, code={}",
                    id, entity.getConnectionCode());
            throw new IllegalStateException("连接已停用: " + entity.getConnectionCode());
        }
        log.info("connectAndSync start: id={}, code={}, transport={}",
                id, entity.getConnectionCode(), entity.getTransport());
        try {
            // 建连 → 同步工具目录 → 清空 lastError → 刷新运行时缓存
            mcpClientRegistry.put(entity);
            int n = mcpToolSyncService.syncConnection(entity.getConnectionCode());
            entity.setLastError(null);
            baseMapper.updateById(entity);
            toolPort.evictAll();
            toolIndexHydrator.refreshAll();
            log.info("connectAndSync ok: id={}, code={}, upserted={}",
                    id, entity.getConnectionCode(), n);
            return n;
        } catch (Exception e) {
            // 失败原因截断写入 last_error，便于后台排查
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.length() > 1000) {
                msg = msg.substring(0, 1000);
            }
            entity.setLastError(msg);
            baseMapper.updateById(entity);
            log.error("connectAndSync failed: id={}, code={}, error={}",
                    id, entity.getConnectionCode(), msg, e);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(msg, e);
        }
    }

    /**
     * 实体转响应 DTO（含工具数、密钥是否已配置）。
     *
     * @param entity 连接实体
     * @return 对外响应对象
     */
    @Override
    public McpConnectionResponse toResponse(AiMcpConnection entity) {
        McpConnectionResponse r = new McpConnectionResponse();
        r.setId(entity.getId());
        r.setConnectionCode(entity.getConnectionCode());
        r.setConnectionName(entity.getConnectionName());
        r.setTransport(entity.getTransport());
        r.setTransportName(McpTransportEnum.labelOf(entity.getTransport()));
        r.setEndpoint(entity.getEndpoint());
        r.setCommand(entity.getCommand());
        r.setArgsJson(entity.getArgsJson());
        r.setHeadersJson(entity.getHeadersJson());
        r.setRequestTimeoutMs(entity.getRequestTimeoutMs());
        r.setEnabled(entity.getEnabled());
        r.setEnabledName(YesNo.labelOf(entity.getEnabled()));
        r.setLastError(entity.getLastError());
        // 仅暴露是否已配置密钥，不回传明文
        r.setSecretConfigured(StringUtils.hasText(entity.getSecret()));
        r.setCreateTime(entity.getCreateTime());
        r.setUpdateTime(entity.getUpdateTime());
        Long count = aiToolService.count(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getSourceType, ToolSourceTypeEnum.MCP.getCode())
                .eq(AiTool::getMcpConnectionCode, entity.getConnectionCode())
                .eq(AiTool::getEnabled, YesNo.YES.getCode()));
        r.setToolCount(count == null ? 0 : count.intValue());
        return r;
    }

    /**
     * 按主键加载连接，不存在则抛异常。
     *
     * @param id 连接主键
     * @return 连接实体
     * @throws IllegalArgumentException 连接不存在
     */
    private AiMcpConnection require(Long id) {
        AiMcpConnection entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("连接不存在: id=" + id);
        }
        return entity;
    }

    /**
     * 校验传输类型与必填字段：STDIO 需 command；SSE 需 endpoint。
     */
    private static void validateTransport(Integer transport, String endpoint, String command) {
        McpTransportEnum t = McpTransportEnum.ofRequired(transport);
        switch (t) {
            case STDIO -> {
                if (!StringUtils.hasText(command)) {
                    throw new IllegalArgumentException("STDIO 必须配置 command");
                }
            }
            case SSE -> {
                if (!StringUtils.hasText(endpoint)) {
                    throw new IllegalArgumentException("SSE 必须配置 endpoint");
                }
            }
        }
    }

    /**
     * 空白字符串转为 {@code null}，非空白则 trim。
     *
     * @param s 原始字符串
     * @return trim 后的值，或 {@code null}
     */
    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
