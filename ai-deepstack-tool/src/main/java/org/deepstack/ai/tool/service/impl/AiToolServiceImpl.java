package org.deepstack.ai.tool.service.impl;


import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.tool.model.dto.request.AiToolCreateRequest;
import org.deepstack.ai.tool.model.dto.request.AiToolPageRequest;
import org.deepstack.ai.tool.model.dto.request.AiToolUpdateRequest;
import org.deepstack.ai.tool.model.dto.response.AiToolResponse;
import org.deepstack.ai.tool.model.entity.AiTool;
import org.deepstack.ai.tool.mapper.AiToolMapper;
import org.deepstack.ai.tool.service.AiToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 工具配置服务实现
 * <p>
 * 对应表 {@code ai_tool}。
 * 工具的参数 schema 从 Java {@code @Tool} 方法签名反射生成，DB 不存 param_schema。
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolServiceImpl extends ServiceImpl<AiToolMapper, AiTool> implements AiToolService {

    private final ToolDescriptionResolver toolDescriptionResolver;

    // ===== 运行时查询 =====

    /**
     * 按工具编码查询已启用工具。
     *
     * @param toolCode 工具编码
     * @return 启用中的工具；不存在或未启用返回 null
     */
    @Override
    public AiTool getByToolCode(String toolCode) {
        log.info("getByToolCode: toolCode={}", toolCode);
        AiTool tool = baseMapper.selectOne(
                new LambdaQueryWrapper<AiTool>()
                        .eq(AiTool::getToolCode, toolCode)
                        .eq(AiTool::getEnabled, YesNo.YES.getCode())
        );
        if (tool == null) {
            log.warn("启用工具未找到: toolCode={}", toolCode);
        }
        return tool;
    }

    /**
     * 按工具编码批量查询已启用工具。
     *
     * @param toolCodes 工具编码列表
     * @return 命中的启用工具；入参空则空列表
     */
    @Override
    public List<AiTool> listEnabledByCodes(List<String> toolCodes) {
        if (toolCodes == null || toolCodes.isEmpty()) {
            log.warn("listEnabledByCodes 跳过: toolCodes 为空");
            return List.of();
        }
        List<String> codes = new ArrayList<>(toolCodes.size());
        for (String c : toolCodes) {
            if (StringUtils.hasText(c)) {
                codes.add(c.trim());
            }
        }
        if (codes.isEmpty()) {
            return List.of();
        }
        log.debug("listEnabledByCodes: size={}", codes.size());
        return baseMapper.selectList(new LambdaQueryWrapper<AiTool>()
                .in(AiTool::getToolCode, codes)
                .eq(AiTool::getEnabled, YesNo.YES.getCode()));
    }

    /**
     * 按主键查询已启用工具。
     *
     * @param id 工具 ID
     * @return 启用中的工具；不存在或未启用返回 null
     */
    @Override
    public AiTool getEnabledById(Long id) {
        log.info("getEnabledById: id={}", id);
        AiTool tool = baseMapper.selectOne(
                new LambdaQueryWrapper<AiTool>()
                        .eq(AiTool::getId, id)
                        .eq(AiTool::getEnabled, YesNo.YES.getCode())
        );
        if (tool == null) {
            log.warn("启用工具未找到: id={}", id);
        }
        return tool;
    }

    /**
     * 列出全部已启用工具（按 id 升序）。
     *
     * @return 启用工具列表
     */
    @Override
    public List<AiTool> listEnabled() {
        log.info("listEnabled: 查询全部启用工具");
        return baseMapper.selectList(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getEnabled, YesNo.YES.getCode())
                .orderByAsc(AiTool::getId));
    }

    // ===== CRUD =====

    /**
     * 分页查询工具。
     *
     * @param req 分页与筛选条件
     * @return 分页结果
     */
    @Override
    public IPage<AiTool> page(AiToolPageRequest req) {
        log.info("page: pageNum={}, pageSize={}, enabled={}, keyword={}",
                req.getPageNum(), req.getPageSize(), req.getEnabled(), req.getKeyword());
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<AiTool>()
                .eq(req.getEnabled() != null, AiTool::getEnabled, req.getEnabled())
                .orderByDesc(AiTool::getUpdateTime);

        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(AiTool::getToolCode, req.getKeyword())
                    .or()
                    .like(AiTool::getToolName, req.getKeyword()));
        }

        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * 按 ID 查询工具详情（含 Java 兜底描述）。
     *
     * @param id 工具 ID
     * @return 响应 DTO；不存在返回 null
     */
    @Override
    public AiToolResponse getDetail(Long id) {
        log.info("getDetail: id={}", id);
        AiTool tool = baseMapper.selectById(id);
        if (tool == null) {
            log.warn("工具未找到: id={}", id);
            return null;
        }
        return toResponse(tool);
    }

    /**
     * 创建工具配置。
     *
     * @param req 创建请求
     * @return 新工具 ID
     */
    @Override
    public Long create(AiToolCreateRequest req) {
        log.info("create: toolCode={}, handlerBean={}", req.getToolCode(), req.getHandlerBean());
        AiTool entity = new AiTool();
        entity.setToolCode(req.getToolCode());
        entity.setToolName(req.getToolName());
        entity.setDescription(req.getDescription());
        entity.setHandlerBean(req.getHandlerBean());
        entity.setSourceType(org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum.LOCAL.getCode());
        entity.setEnabled(req.getEnabled());
        baseMapper.insert(entity);
        log.info("Created ai tool: id={}, toolCode={}, handlerBean={}",
                entity.getId(), entity.getToolCode(), entity.getHandlerBean());
        return entity.getId();
    }

    /**
     * 更新工具配置。
     * <p>
     * MCP 来源工具禁止改 handlerBean（须走连接同步）；LOCAL 工具可改 handlerBean。
     * description 传空串表示清空并回退 Java 兜底，null 表示不修改。
     * </p>
     *
     * @param req 更新请求（须含 id）
     * @throws IllegalArgumentException 工具不存在，或 MCP 工具试图修改 handlerBean
     */
    @Override
    public void update(AiToolUpdateRequest req) {
        log.info("update: id={}", req.getId());
        AiTool entity = baseMapper.selectById(req.getId());
        if (entity == null) {
            log.warn("update 失败: 工具不存在 id={}", req.getId());
            throw new IllegalArgumentException("工具不存在: id=" + req.getId());
        }
        // MCP 工具的 handler 由连接同步写入，禁止管理端手工改绑
        boolean mcp = entity.getSourceType() != null
                && entity.getSourceType() == org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum.MCP.getCode();
        if (mcp && req.getHandlerBean() != null) {
            throw new IllegalArgumentException("MCP 工具不可修改 handlerBean，请走连接同步");
        }
        if (req.getToolCode() != null) entity.setToolCode(req.getToolCode());
        if (req.getToolName() != null) entity.setToolName(req.getToolName());
        // description 允许传空串表示清空（回退到 Java 兜底）；null 表示不修改
        if (req.getDescription() != null) entity.setDescription(req.getDescription());
        // 仅 LOCAL 工具允许更新 handlerBean；MCP 已在上方拦截
        if (!mcp && req.getHandlerBean() != null) entity.setHandlerBean(req.getHandlerBean());
        if (req.getEnabled() != null) entity.setEnabled(req.getEnabled());
        baseMapper.updateById(entity);
        log.info("Updated ai tool: id={}", req.getId());
    }

    /**
     * 启用工具。
     *
     * @param id 工具 ID
     * @throws IllegalArgumentException 工具不存在
     */
    @Override
    public void enable(Long id) {
        log.info("enable: id={}", id);
        AiTool entity = baseMapper.selectById(id);
        if (entity == null) {
            log.warn("enable 失败: 工具不存在 id={}", id);
            throw new IllegalArgumentException("工具不存在: id=" + id);
        }
        entity.setEnabled(YesNo.YES.getCode());
        baseMapper.updateById(entity);
        log.info("Enabled ai tool: id={}, toolCode={}", id, entity.getToolCode());
    }

    /**
     * 禁用工具。
     *
     * @param id 工具 ID
     * @throws IllegalArgumentException 工具不存在
     */
    @Override
    public void disable(Long id) {
        log.info("disable: id={}", id);
        AiTool entity = baseMapper.selectById(id);
        if (entity == null) {
            log.warn("disable 失败: 工具不存在 id={}", id);
            throw new IllegalArgumentException("工具不存在: id=" + id);
        }
        entity.setEnabled(YesNo.NO.getCode());
        baseMapper.updateById(entity);
        log.info("Disabled ai tool: id={}, toolCode={}", id, entity.getToolCode());
    }


    // ===== 私有辅助 =====

    /**
     * 工具实体转响应 DTO。
     * <p>
     * 填充 Java {@code @Tool} 回退描述；sourceType 为空时按 LOCAL 展示；
     * MCP 字段（连接编码、远端工具名）原样带出。
     * </p>
     *
     * @param t 工具实体
     * @return 管理端详情响应 DTO
     */
    private AiToolResponse toResponse(AiTool t) {
        AiToolResponse r = new AiToolResponse();
        r.setId(t.getId());
        r.setToolCode(t.getToolCode());
        r.setToolName(t.getToolName());
        r.setDescription(t.getDescription());
        r.setJavaFallbackDescription(toolDescriptionResolver.resolveJavaDescription(t.getHandlerBean()));
        r.setHandlerBean(t.getHandlerBean());
        Integer sourceType = t.getSourceType() != null
                ? t.getSourceType()
                : org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum.LOCAL.getCode();
        r.setSourceType(sourceType);
        r.setSourceTypeName(org.deepstack.ai.kernel.enums.tool.ToolSourceTypeEnum.labelOf(sourceType));
        r.setMcpConnectionCode(t.getMcpConnectionCode());
        r.setMcpToolName(t.getMcpToolName());
        r.setEnabled(t.getEnabled());
        r.setEnabledName(org.deepstack.ai.kernel.enums.common.EnabledStatusEnum.labelOf(t.getEnabled()));
        r.setCreateTime(t.getCreateTime());
        r.setUpdateTime(t.getUpdateTime());
        return r;
    }
}
