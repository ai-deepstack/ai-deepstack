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
                        .and(w -> w.isNull(AiAgent::getTemplate).or().eq(AiAgent::getTemplate, YesNo.NO.getCode()))
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
                .and(w -> w.isNull(AiAgent::getTemplate).or().eq(AiAgent::getTemplate, YesNo.NO.getCode()))
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
     * <p>agentCode 自动生成；编排模式默认 CHAT；trace 默认 RECORD。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AiAgentCreateRequest req) {
        AiAgent entity = new AiAgent();
        entity.setAgentCode(nextUniqueAgentCode());
        entity.setAgentName(req.getAgentName());
        entity.setModelCode(req.getModelCode());
        entity.setSystemPrompt(req.getSystemPrompt());
        entity.setTemperature(req.getTemperature());
        entity.setMaxTokens(req.getMaxTokens());
        entity.setTopP(req.getTopP());
        entity.setMemoryMaxMessages(req.getMemoryMaxMessages());
        entity.setEnableMemory(req.getEnableMemory());
        entity.setResponseFormat(req.getResponseFormat());
        entity.setResponseSchema(req.getResponseSchema());
        entity.setEnabled(req.getEnabled());
        entity.setOrchestrateMode(req.getOrchestrateMode() != null
                ? req.getOrchestrateMode()
                : org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum.CHAT.getCode());
        entity.setGraphDefinition(req.getGraphDefinition());
        entity.setGraphVersion(1);
        entity.setTemplate(YesNo.NO.getCode());
        entity.setPublishedGraphDefinition(null);
        entity.setPublishedVersion(null);
        entity.setTraceMode(req.getTraceMode() != null
                ? req.getTraceMode()
                : org.deepstack.ai.kernel.enums.agent.TraceModeEnum.RECORD.getCode());
        entity.setStreamProgress(req.getStreamProgress() != null ? req.getStreamProgress() : YesNo.NO.getCode());
        entity.setCoverUrl(StringUtils.hasText(req.getCoverUrl()) ? req.getCoverUrl().trim() : null);
        entity.setQuotaQps(positiveOrNull(req.getQuotaQps()));
        entity.setQuotaConcurrency(positiveOrNull(req.getQuotaConcurrency()));
        entity.setQuotaDailyTokens(positiveOrNull(req.getQuotaDailyTokens()));
        entity.setHitlTimeoutMinutes(positiveOrNull(req.getHitlTimeoutMinutes()));
        baseMapper.insert(entity);
        if (req.getKnowledgeBaseCodes() != null && !req.getKnowledgeBaseCodes().isEmpty()) {
            bindKnowledgeBases(entity.getId(), req.getKnowledgeBaseCodes());
        }
        log.info("Created ai agent: id={}, agentCode={}, orchestrateMode={}",
                entity.getId(), entity.getAgentCode(), entity.getOrchestrateMode());
        return entity.getId();
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
        if (req.getAgentName() != null) entity.setAgentName(req.getAgentName());
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
     * {@inheritDoc}
     */
    @Override
    public IPage<AiAgent> pageTemplates(AiAgentPageRequest req) {
        log.info("pageTemplates: pageNum={}, pageSize={}, keyword={}",
                req.getPageNum(), req.getPageSize(), req.getKeyword());
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getTemplate, YesNo.YES.getCode())
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
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAsTemplate(Long sourceId, String agentCode, String agentName) {
        AiAgent source = requireAgent(sourceId);
        AiAgent copy = copyAgentShell(source, agentCode, agentName, true);
        baseMapper.insert(copy);
        copyBindings(source.getId(), copy.getId());
        log.info("saveAsTemplate: sourceId={}, templateId={}, code={}", sourceId, copy.getId(), copy.getAgentCode());
        return copy.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromTemplate(Long templateId, String agentCode, String agentName) {
        AiAgent template = requireAgent(templateId);
        if (!YesNo.isYes(template.getTemplate())) {
            log.warn("createFromTemplate: 非模板 agentId={}", templateId);
            throw new BusinessException(CommonErrorCode.BAD_REQUEST.getCode(), "源记录不是模板智能体");
        }
        AiAgent copy = copyAgentShell(template, agentCode, agentName, false);
        baseMapper.insert(copy);
        copyBindings(template.getId(), copy.getId());
        log.info("createFromTemplate: templateId={}, agentId={}, code={}", templateId, copy.getId(), copy.getAgentCode());
        return copy.getId();
    }

    /**
     * 复制智能体配置壳（不含 id / 绑定）；模板与普通智能体字段差异由 {@code asTemplate} 控制。
     *
     * @param source     源实体
     * @param agentCode  新编码（可空自动生成）
     * @param agentName  新名称（可空沿用）
     * @param asTemplate true=模板行；false=普通智能体
     * @return 未落库的新实体
     */
    private AiAgent copyAgentShell(AiAgent source, String agentCode, String agentName, boolean asTemplate) {
        AiAgent copy = new AiAgent();
        copy.setAgentCode(resolveNewAgentCode(agentCode));
        copy.setAgentName(StringUtils.hasText(agentName) ? agentName.trim() : source.getAgentName());
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
        // 草稿优先用源草稿；无草稿时用已发布定义作为草稿
        String draft = StringUtils.hasText(source.getGraphDefinition())
                ? source.getGraphDefinition()
                : source.getPublishedGraphDefinition();
        copy.setGraphDefinition(draft);
        copy.setTemplate(asTemplate ? YesNo.YES.getCode() : YesNo.NO.getCode());
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
     * 解析新 agentCode：有值则校验唯一，否则自动生成。
     *
     * @param agentCode 请求编码
     * @return 可用编码
     */
    private String resolveNewAgentCode(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            return nextUniqueAgentCode();
        }
        String code = agentCode.trim();
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<AiAgent>().eq(AiAgent::getAgentCode, code));
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT.getCode(), "智能体编码已存在: " + code);
        }
        return code;
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
