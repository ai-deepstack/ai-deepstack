package org.deepstack.ai.tool.mcp;

import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.tool.model.entity.AiTool;
import org.deepstack.ai.tool.service.AiToolService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * listTools → upsert {@code ai_tool}（{@code source_type=MCP}）；远端消失的工具自动停用。
 * <p>
 * toolCode 规则：{@code {prefix}{connectionCode}_{sanitize(remoteName)}}，
 * 例如 {@code mcp_fs_list_dir}。Agent 绑定的是 toolCode，不是连接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolSyncService {

    private final McpToolBridge mcpToolBridge;
    private final AiToolService aiToolService;
    private final SysConfigPort sysConfigPort;

    /**
     * 同步指定连接的远端工具到 {@code ai_tool}：upsert 现存工具，停用远端已消失的工具。
     *
     * @param connectionCode 连接编码
     * @return 本次 upsert 条数（新建 + 更新）
     * @throws IllegalArgumentException 连接编码为空
     * @throws IllegalStateException    Client 未就绪等运行时错误
     */
    @Transactional
    public int syncConnection(String connectionCode) {
        if (!StringUtils.hasText(connectionCode)) {
            throw new IllegalArgumentException("connectionCode 不能为空");
        }
        String code = connectionCode.trim();
        log.info("McpToolSyncService: sync start connection={}", code);
        List<McpSchema.Tool> remoteTools = mcpToolBridge.listRemoteTools(code);
        Set<String> seenCodes = new HashSet<>();
        int inserted = 0;
        int updated = 0;
        for (McpSchema.Tool remote : remoteTools) {
            if (remote == null || !StringUtils.hasText(remote.name())) {
                log.warn("McpToolSyncService: skip remote tool with blank name, connection={}", code);
                continue;
            }
            String toolCode = toToolCode(code, remote.name());
            seenCodes.add(toolCode);
            if (upsertOne(code, remote, toolCode)) {
                inserted++;
            } else {
                updated++;
            }
        }
        // 远端已不存在的平台工具自动停用
        int disabled = disableMissing(code, seenCodes);
        // 同步后清 Bridge 缓存，避免沿用旧 Tool 定义
        mcpToolBridge.evictConnection(code);
        log.info("McpToolSyncService: sync done connection={}, remoteSize={}, inserted={}, updated={}, disabled={}",
                code, remoteTools.size(), inserted, updated, disabled);
        return inserted + updated;
    }

    /**
     * 按前缀 + 连接编码 + 清洗后的远端名生成平台 toolCode。
     *
     * @param connectionCode 连接编码
     * @param remoteName     远端工具名
     * @return 平台 toolCode
     */
    public String toToolCode(String connectionCode, String remoteName) {
        String prefix = sysConfigPort.getString(SysConfigKeys.MCP_TOOL_CODE_PREFIX);
        if (!StringUtils.hasText(prefix)) {
            prefix = "mcp_";
        }
        return prefix + connectionCode.trim() + "_" + sanitize(remoteName);
    }

    /**
     * 按 toolCode upsert 一条 MCP 工具记录。
     *
     * @param connectionCode 连接编码
     * @param remote         远端工具元数据
     * @param toolCode       平台工具编码
     * @return {@code true}=新建，{@code false}=更新
     */
    private boolean upsertOne(String connectionCode, McpSchema.Tool remote, String toolCode) {
        AiTool existing = aiToolService.getOne(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getToolCode, toolCode)
                .last("LIMIT 1"), false);
        String displayName = StringUtils.hasText(remote.title()) ? remote.title() : remote.name();
        String description = remote.description();
        if (existing == null) {
            AiTool entity = new AiTool();
            entity.setToolCode(toolCode);
            entity.setToolName(displayName);
            entity.setDescription(description);
            entity.setHandlerBean(null);
            entity.setSourceType(ToolSourceTypeEnum.MCP.getCode());
            entity.setMcpConnectionCode(connectionCode);
            entity.setMcpToolName(remote.name());
            entity.setEnabled(YesNo.YES.getCode());
            aiToolService.save(entity);
            log.info("McpToolSyncService: inserted toolCode={}, remote={}, connection={}",
                    toolCode, remote.name(), connectionCode);
            return true;
        }
        existing.setToolName(displayName);
        if (StringUtils.hasText(description)) {
            existing.setDescription(description);
        }
        existing.setSourceType(ToolSourceTypeEnum.MCP.getCode());
        existing.setMcpConnectionCode(connectionCode);
        existing.setMcpToolName(remote.name());
        existing.setHandlerBean(null);
        // 远端仍存在则重新启用（可能此前因缺失被停用）
        existing.setEnabled(YesNo.YES.getCode());
        aiToolService.updateById(existing);
        log.debug("McpToolSyncService: updated toolCode={}, remote={}, connection={}",
                toolCode, remote.name(), connectionCode);
        return false;
    }

    /**
     * 停用本连接下不在本次同步集合中的 MCP 工具。
     *
     * @param connectionCode 连接编码
     * @param keepToolCodes  本次应保留（仍启用）的 toolCode 集合
     * @return 本次停用条数
     */
    private int disableMissing(String connectionCode, Set<String> keepToolCodes) {
        List<AiTool> existing = aiToolService.list(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getSourceType, ToolSourceTypeEnum.MCP.getCode())
                .eq(AiTool::getMcpConnectionCode, connectionCode));
        int disabled = 0;
        for (AiTool t : existing) {
            if (t.getToolCode() != null && !keepToolCodes.contains(t.getToolCode())
                    && (t.getEnabled() == null || t.getEnabled() == YesNo.YES.getCode())) {
                t.setEnabled(YesNo.NO.getCode());
                aiToolService.updateById(t);
                disabled++;
                log.info("McpToolSyncService: disabled missing MCP tool toolCode={}, connection={}",
                        t.getToolCode(), connectionCode);
            }
        }
        return disabled;
    }

    /**
     * 将远端工具名清洗为 toolCode 安全片段（小写、非字母数字转下划线）。
     *
     * @param remoteName 远端工具名
     * @return 清洗后的片段；全空时回退为 {@code tool}
     */
    private static String sanitize(String remoteName) {
        String s = remoteName.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9_]+", "_");
        s = s.replaceAll("_+", "_");
        if (s.startsWith("_")) {
            s = s.substring(1);
        }
        if (s.endsWith("_")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "tool" : s;
    }

    /**
     * 调试用：远端 inputSchema 摘要（当前不落库）。
     *
     * @param remote 远端工具
     * @return schema JSON 字符串；无 schema 时 {@code null}
     */
    @SuppressWarnings("unused")
    private static String schemaHint(McpSchema.Tool remote) {
        if (remote.inputSchema() == null) {
            return null;
        }
        return JSON.toJSONString(remote.inputSchema());
    }
}
