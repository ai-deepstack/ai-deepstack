package org.deepstack.ai.tool.service;

import org.deepstack.ai.tool.model.dto.request.AiToolCreateRequest;
import org.deepstack.ai.tool.model.dto.request.AiToolPageRequest;
import org.deepstack.ai.tool.model.dto.request.AiToolUpdateRequest;
import org.deepstack.ai.tool.model.dto.response.AiToolResponse;
import org.deepstack.ai.tool.model.entity.AiTool;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * AI 工具目录服务（不含智能体绑定，绑定在 agent 编排域）。
 * <p>
 * 对应表 {@code ai_tool}：运行时按编码查启用工具，管理端做分页 CRUD / 启停。
 * </p>
 */
public interface AiToolService extends IService<AiTool> {

    /**
     * 按工具编码查询已启用工具。
     *
     * @param toolCode 工具编码
     * @return 启用中的工具；不存在或未启用返回 null
     */
    AiTool getByToolCode(String toolCode);

    /**
     * 按工具编码批量查询已启用工具（一次 IN，避免 N 次单查）。
     *
     * @param toolCodes 工具编码列表
     * @return 命中的启用工具；入参空则空列表
     */
    List<AiTool> listEnabledByCodes(List<String> toolCodes);

    /**
     * 列出全部已启用工具（按 id 升序）。
     *
     * @return 启用工具列表
     */
    List<AiTool> listEnabled();

    /**
     * 按主键查询已启用工具。
     *
     * @param id 工具 ID
     * @return 启用中的工具；不存在或未启用返回 null
     */
    AiTool getEnabledById(Long id);

    /**
     * 分页查询工具（可按启用状态、关键字筛选）。
     *
     * @param req 分页与筛选条件
     * @return 分页结果
     */
    IPage<AiTool> page(AiToolPageRequest req);

    /**
     * 按 ID 查询工具详情（含 Java {@code @Tool} 兜底描述）。
     *
     * @param id 工具 ID
     * @return 响应 DTO；不存在返回 null
     */
    AiToolResponse getDetail(Long id);

    /**
     * 创建 LOCAL 工具配置。
     *
     * @param req 创建请求
     * @return 新工具 ID
     */
    Long create(AiToolCreateRequest req);

    /**
     * 更新工具配置。
     * <p>MCP 来源工具不可修改 handlerBean，须走连接同步。</p>
     *
     * @param req 更新请求（须含 id）
     * @throws IllegalArgumentException 工具不存在，或 MCP 工具试图修改 handlerBean
     */
    void update(AiToolUpdateRequest req);

    /**
     * 启用工具。
     *
     * @param id 工具 ID
     * @throws IllegalArgumentException 工具不存在
     */
    void enable(Long id);

    /**
     * 禁用工具。
     *
     * @param id 工具 ID
     * @throws IllegalArgumentException 工具不存在
     */
    void disable(Long id);
}

