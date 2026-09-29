package org.deepstack.ai.agent.service.impl;

import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.agent.model.dto.request.AiAgentCreateRequest;
import org.deepstack.ai.agent.model.dto.request.AiAgentPageRequest;
import org.deepstack.ai.agent.model.dto.request.AiAgentUpdateRequest;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.agent.model.entity.AiAgentKnowledgeBase;
import org.deepstack.ai.agent.model.entity.AiAgentTool;
import org.deepstack.ai.agent.mapper.AiAgentKnowledgeBaseMapper;
import org.deepstack.ai.agent.mapper.AiAgentMapper;
import org.deepstack.ai.agent.mapper.AiAgentToolMapper;
import org.deepstack.ai.agent.service.AiAgentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@link AiAgentService} 实现：智能体 CRUD、启停、知识库绑定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl extends ServiceImpl<AiAgentMapper, AiAgent> implements AiAgentService {

    private final AiAgentKnowledgeBaseMapper aiAgentKnowledgeBaseMapper;
    private final AiAgentToolMapper aiAgentToolMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public AiAgent getByAgentCode(String agentCode) {
        log.info("getByAgentCode: agentCode={}", agentCode);
        AiAgent agent = baseMapper.selectOne(
                new LambdaQueryWrapper<AiAgent>()
                        .eq(AiAgent::getAgentCode, agentCode)
                        .eq(AiAgent::getEnabled, YesNo.YES.getCode())
        );
        if (agent == null) {
            log.warn("启用智能体未找到: agentCode={}", agentCode);
        }
        return agent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AiAgent> listEnabled() {
        log.debug("listEnabled");
        return baseMapper.selectList(
                new LambdaQueryWrapper<AiAgent>()
                        .eq(AiAgent::getEnabled, YesNo.YES.getCode())
                        .orderByAsc(AiAgent::getId)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getKnowledgeBaseCodes(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return aiAgentKnowledgeBaseMapper.selectBaseCodesByAgentId(agentId);
    }

    /**
     * {@inheritDoc}
     * <p>先删后插；列表下标即 priority。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindKnowledgeBases(Long agentId, List<String> knowledgeBaseCodes) {
        aiAgentKnowledgeBaseMapper.deleteByAgentId(agentId);
        if (knowledgeBaseCodes != null && !knowledgeBaseCodes.isEmpty()) {
            for (int i = 0; i < knowledgeBaseCodes.size(); i++) {
                AiAgentKnowledgeBase rel = new AiAgentKnowledgeBase();
                rel.setAgentId(agentId);
                rel.setKnowledgeBaseCode(knowledgeBaseCodes.get(i));
                rel.setPriority(i);
                aiAgentKnowledgeBaseMapper.insert(rel);
            }
        }
        log.info("bound knowledge bases to agent: agentId={}, codes={}", agentId, knowledgeBaseCodes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IPage<AiAgent> page(AiAgentPageRequest req) {
        log.debug("page: pageNum={}, pageSize={}, keyword={}",
                req.getPageNum(), req.getPageSize(), req.getKeyword());
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<AiAgent>()
                .eq(StringUtils.hasText(req.getModelCode()), AiAgent::getModelCode, req.getModelCode())
                .eq(req.getEnabled() != null, AiAgent::getEnabled, req.getEnabled())
                .orderByDesc(AiAgent::getUpdateTime);

        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(AiAgent::getAgentCode, req.getKeyword())
                    .or()
                    .like(AiAgent::getAgentName, req.getKeyword()));
        }
        if (StringUtils.hasText(req.getAgentCode())) {
            wrapper.like(AiAgent::getAgentCode, req.getAgentCode());
        }

        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * {@inheritDoc}
     * <p>agentCode 始终服务端生成；名称全局唯一。有 sourceAgentId 时从源复制后覆盖请求字段，副本默认停用。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AiAgentCreateRequest req) {
        String agentName = requireUniqueAgentName(req.getAgentName(), null);
        Long sourceId = req.getSourceAgentId();
        AiAgent entity;
        if (sourceId != null) {
            AiAgent source = requireAgent(sourceId);
            entity = copyAgentShell(source, agentName);
            applyCreateFields(entity, req, agentName);
            entity.setEnabled(YesNo.NO.getCode());
            entity.setPublishedGraphDefinition(null);
            entity.setPublishedVersion(null);
            entity.setGraphVersion(1);
            if (!StringUtils.hasText(entity.getGraphDefinition())) {
                entity.setGraphDefinition(source.getPublishedGraphDefinition());
            }
        } else {
            entity = new AiAgent();
            entity.setAgentCode(nextUniqueAgentCode());
            applyCreateFields(entity, req, agentName);
            entity.setEnabled(req.getEnabled() != null ? req.getEnabled() : YesNo.YES.getCode());
            entity.setGraphDefinition(req.getGraphDefinition());
            entity.setGraphVersion(1);
            entity.setPublishedGraphDefinition(null);
            entity.setPublishedVersion(null);
        }
        baseMapper.insert(entity);
        if (sourceId != null) {
            copyBindings(sourceId, entity.getId());
            if (req.getKnowledgeBaseCodes() != null) {
                bindKnowledgeBases(entity.getId(), req.getKnowledgeBaseCodes());
            }
        } else if (req.getKnowledgeBaseCodes() != null && !req.getKnowledgeBaseCodes().isEmpty()) {
            bindKnowledgeBases(entity.getId(), req.getKnowledgeBaseCodes());
        }
        log.info("Created ai agent: id={}, agentCode={}, sourceId={}, orchestrateMode={}",
                entity.getId(), entity.getAgentCode(), sourceId, entity.getOrchestrateMode());
        return entity.getId();
    }

    /**
     * 将创建请求中的可写字段落到实体（不含编码 / 发布态 / 启停策略）。
     */
    private void applyCreateFields(AiAgent entity, AiAgentCreateRequest req, String agentName) {
        entity.setAgentName(agentName);
        entity.setModelCode(req.getModelCode());
        entity.setSystemPrompt(req.getSystemPrompt());
        entity.setTemperature(req.getTemperature());
        entity.setMaxTokens(req.getMaxTokens());
        entity.setTopP(req.getTopP());
        entity.setMemoryMaxMessages(req.getMemoryMaxMessages());
        entity.setEnableMemory(req.getEnableMemory());
        entity.setResponseFormat(req.getResponseFormat());
        entity.setResponseSchema(req.getResponseSchema());
        entity.setOrchestrateMode(req.getOrchestrateMode() != null
                ? req.getOrchestrateMode()
                : org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum.CHAT.getCode());
        if (req.getGraphDefinition() != null) {
            entity.setGraphDefinition(req.getGraphDefinition());
        }
        entity.setTraceMode(req.getTraceMode() != null
                ? req.getTraceMode()
                : org.deepstack.ai.kernel.enums.agent.TraceModeEnum.RECORD.getCode());
        entity.setStreamProgress(req.getStreamProgress() != null ? req.getStreamProgress() : YesNo.NO.getCode());
        entity.setCoverUrl(StringUtils.hasText(req.getCoverUrl()) ? req.getCoverUrl().trim() : null);
        entity.setQuotaQps(positiveOrNull(req.getQuotaQps()));
        entity.setQuotaConcurrency(positiveOrNull(req.getQuotaConcurrency()));
        entity.setQuotaDailyTokens(positiveOrNull(req.getQuotaDailyTokens()));
        entity.setHitlTimeoutMinutes(positiveOrNull(req.getHitlTimeoutMinutes()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiAgentUpdateRequest req) {
        AiAgent entity = baseMapper.selectById(req.getId());
        if (entity == null) {
            throw new IllegalArgumentException("智能体不存在: id=" + req.getId());
        }
        if (req.getAgentName() != null) {
            String name = requireUniqueAgentName(req.getAgentName(), req.getId());
            entity.setAgentName(name);
        }
        if (req.getModelCode() != null) entity.setModelCode(req.getModelCode());
        if (req.getSystemPrompt() != null) entity.setSystemPrompt(req.getSystemPrompt());
        if (req.getTemperature() != null) entity.setTemperature(req.getTemperature());
        if (req.getMaxTokens() != null) entity.setMaxTokens(req.getMaxTokens());
        if (req.getTopP() != null) entity.setTopP(req.getTopP());
        if (req.getMemoryMaxMessages() != null) entity.setMemoryMaxMessages(req.getMemoryMaxMessages());
        if (req.getEnableMemory() != null) entity.setEnableMemory(req.getEnableMemory());
        if (req.getResponseFormat() != null) entity.setResponseFormat(req.getResponseFormat());
        if (req.getResponseSchema() != null) entity.setResponseSchema(req.getResponseSchema());
        if (req.getEnabled() != null) entity.setEnabled(req.getEnabled());
        if (req.getOrchestrateMode() != null) entity.setOrchestrateMode(req.getOrchestrateMode());
        if (req.getGraphDefinition() != null) entity.setGraphDefinition(req.getGraphDefinition());
        if (req.getTraceMode() != null) entity.setTraceMode(req.getTraceMode());
        if (req.getStreamProgress() != null) entity.setStreamProgress(req.getStreamProgress());
        if (req.getCoverUrl() != null) {
            entity.setCoverUrl(StringUtils.hasText(req.getCoverUrl()) ? req.getCoverUrl().trim() : null);
        }
        if (req.getQuotaQps() != null) {
            entity.setQuotaQps(positiveOrNull(req.getQuotaQps()));
        }
        if (req.getQuotaConcurrency() != null) {
            entity.setQuotaConcurrency(positiveOrNull(req.getQuotaConcurrency()));
        }
        if (req.getQuotaDailyTokens() != null) {
            entity.setQuotaDailyTokens(positiveOrNull(req.getQuotaDailyTokens()));
        }
        if (req.getHitlTimeoutMinutes() != null) {
            entity.setHitlTimeoutMinutes(positiveOrNull(req.getHitlTimeoutMinutes()));
        }
        baseMapper.updateById(entity);
        // null 表示不改绑定；非 null（含空列表）表示全量重绑
        if (req.getKnowledgeBaseCodes() != null) {
            bindKnowledgeBases(req.getId(), req.getKnowledgeBaseCodes());
        }
        log.info("Updated ai agent: id={}", req.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enable(Long id) {
        AiAgent entity = requireAgent(id);
        entity.setEnabled(YesNo.YES.getCode());
        baseMapper.updateById(entity);
        log.info("Enabled ai agent: id={}, code={}", id, entity.getAgentCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disable(Long id) {
        AiAgent entity = requireAgent(id);
        entity.setEnabled(YesNo.NO.getCode());
        baseMapper.updateById(entity);
        log.info("Disabled ai agent: id={}, code={}", id, entity.getAgentCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        aiAgentKnowledgeBaseMapper.deleteByAgentId(id);
        baseMapper.deleteById(id);
        log.info("Deleted ai agent and KB bindings: id={}", id);
    }

    /**
     * 复制智能体配置壳（不含 id / 绑定）；编码服务端生成，默认停用、清空发布态。
     *
     * @param source    源实体
     * @param agentName 已校验唯一的新名称
     * @return 未落库的新实体
     */
    private AiAgent copyAgentShell(AiAgent source, String agentName) {
        AiAgent copy = new AiAgent();
        copy.setAgentCode(nextUniqueAgentCode());
        copy.setAgentName(agentName);
        copy.setSystemPrompt(source.getSystemPrompt());
        copy.setModelCode(source.getModelCode());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTopP(source.getTopP());
        copy.setMemoryMaxMessages(source.getMemoryMaxMessages());
        copy.setEnableMemory(source.getEnableMemory());
        copy.setEnableLongTermMemory(source.getEnableLongTermMemory());
        copy.setEnableGraphMemory(source.getEnableGraphMemory());
        copy.setResponseFormat(source.getResponseFormat());
        copy.setResponseSchema(source.getResponseSchema());
        copy.setOrchestrateMode(source.getOrchestrateMode());
        copy.setTraceMode(source.getTraceMode());
        copy.setStreamProgress(source.getStreamProgress());
        copy.setCoverUrl(source.getCoverUrl());
        copy.setQuotaQps(source.getQuotaQps());
        copy.setQuotaConcurrency(source.getQuotaConcurrency());
        copy.setQuotaDailyTokens(source.getQuotaDailyTokens());
        copy.setHitlTimeoutMinutes(source.getHitlTimeoutMinutes());
        copy.setEnabled(YesNo.NO.getCode());
        copy.setGraphVersion(1);
        copy.setPublishedGraphDefinition(null);
        copy.setPublishedVersion(null);
        String draft = StringUtils.hasText(source.getGraphDefinition())
                ? source.getGraphDefinition()
                : source.getPublishedGraphDefinition();
        copy.setGraphDefinition(draft);
        return copy;
    }

    /**
     * 复制知识库与工具绑定到目标智能体。
     *
     * @param fromAgentId 源
     * @param toAgentId   目标
     */
    private void copyBindings(Long fromAgentId, Long toAgentId) {
        List<String> kbCodes = getKnowledgeBaseCodes(fromAgentId);
        if (!kbCodes.isEmpty()) {
            bindKnowledgeBases(toAgentId, kbCodes);
        }
        List<AiAgentTool> tools = aiAgentToolMapper.selectList(
                new LambdaQueryWrapper<AiAgentTool>()
                        .eq(AiAgentTool::getAgentId, fromAgentId)
                        .orderByAsc(AiAgentTool::getPriority));
        for (AiAgentTool t : tools) {
            AiAgentTool rel = new AiAgentTool();
            rel.setAgentId(toAgentId);
            rel.setToolId(t.getToolId());
            rel.setPriority(t.getPriority());
            rel.setEnabled(t.getEnabled() != null ? t.getEnabled() : YesNo.YES.getCode());
            aiAgentToolMapper.insert(rel);
        }
        log.info("copyBindings: from={}, to={}, kb={}, tools={}",
                fromAgentId, toAgentId, kbCodes.size(), tools.size());
    }

    /**
     * 校验智能体名称全局唯一（trim 后精确匹配）。
     *
     * @param agentName 名称
     * @param excludeId 更新时排除自身；新建传 null
     * @return trim 后的名称
     */
    private String requireUniqueAgentName(String agentName, Long excludeId) {
        if (!StringUtils.hasText(agentName)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(), "智能体名称不能为空");
        }
        String name = agentName.trim();
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getAgentName, name);
        if (excludeId != null) {
            wrapper.ne(AiAgent::getId, excludeId);
        }
        Long count = baseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT.getCode(), "智能体名称已存在");
        }
        return name;
    }

    /**
     * 按主键加载智能体，不存在则抛异常。
     *
     * @param id 智能体主键
     * @return 实体
     * @throws IllegalArgumentException 不存在
     */
    private AiAgent requireAgent(Long id) {
        AiAgent entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("智能体不存在: id=" + id);
        }
        return entity;
    }

    /**
     * 生成唯一智能体编码：DS + yyyyMMdd + 当日序号（3 位起）。
     * 例：DS20260914001
     *
     * @return 唯一 agentCode
     * @throws IllegalStateException 多次碰撞仍无法生成
     */
    private String nextUniqueAgentCode() {
        String prefix = "DS" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String maxCode = baseMapper.selectMaxAgentCodeByPrefix(prefix);
        int nextSeq = 1;
        if (StringUtils.hasText(maxCode) && maxCode.length() > prefix.length()) {
            try {
                nextSeq = Integer.parseInt(maxCode.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                nextSeq = 1;
            }
        }
        if (nextSeq < 1) {
            nextSeq = 1;
        }
        for (int i = 0; i < 20; i++) {
            String code = prefix + String.format("%03d", nextSeq + i);
            Long count = baseMapper.selectCount(
                    new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getAgentCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new IllegalStateException("无法生成唯一的智能体编码，请重试");
    }

    /**
     * 将 ≤0 的配额/超时视为「不限 / 走全局」，存 null。
     *
     * @param value 请求值
     * @return 正整数或 null
     */
    private static Integer positiveOrNull(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }
}
