package org.deepstack.ai.agent.web;

import org.deepstack.ai.agent.model.dto.request.AiAgentCreateRequest;
import org.deepstack.ai.agent.model.dto.request.AiAgentPageRequest;
import org.deepstack.ai.agent.model.dto.request.AiAgentUpdateRequest;
import org.deepstack.ai.agent.model.dto.response.AiAgentResponse;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.agent.model.entity.AiAgent;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.agent.service.AiAgentService;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import org.deepstack.ai.kernel.model.SelectOption;
import org.deepstack.ai.kernel.enums.common.EnabledStatusEnum;
import org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum;
import org.deepstack.ai.kernel.enums.common.YesNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能体管理 API：CRUD、启停、知识库绑定。
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AiAgentService aiAgentService;
    private final AiModelService aiModelService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 分页查询智能体，并填充模型名与知识库绑定。
     *
     * @param req 分页与筛选
     * @return 分页响应
     */
    @GetMapping("/page")
    public Response<PageInfo<AiAgentResponse>> page(AiAgentPageRequest req) {
        log.info("API agents page: pageNum={}, pageSize={}, keyword={}",
                req != null ? req.getPageNum() : null,
                req != null ? req.getPageSize() : null,
                req != null ? req.getKeyword() : null);
        PageInfo<AiAgentResponse> pageInfo = PageInfoUtils.of(
                aiAgentService.page(req), this::toResponseWithoutModelName);

        Set<String> modelCodes = pageInfo.getList().stream()
                .map(AiAgentResponse::getModelCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> modelNameMap = batchGetModelNames(modelCodes);

        pageInfo.getList().forEach(r -> {
            fillKnowledgeBases(r);
            if (r.getModelCode() != null) {
                r.setModelName(modelNameMap.get(r.getModelCode()));
            }
        });

        return Response.success(pageInfo);
    }

    /**
     * 启用智能体下拉选项（value=agentCode / label=名称 · 编排模式）。
     */
    @GetMapping("/options")
    public Response<List<SelectOption>> options() {
        log.info("API agents options");
        List<SelectOption> options = aiAgentService.listEnabled().stream()
                .map(a -> SelectOption.of(
                        a.getAgentCode(),
                        a.getAgentName() + " · " + OrchestrateModeEnum.labelOf(a.getOrchestrateMode())))
                .toList();
        return Response.success(options);
    }

    /**
     * 按主键查询智能体详情。
     *
     * @param id 智能体主键
     * @return 详情；不存在时 data 为 null
     */
    @GetMapping("/{id}")
    public Response<AiAgentResponse> get(@PathVariable("id") Long id) {
        log.info("API get agent: id={}", id);
        AiAgent agent = aiAgentService.getById(id);
        if (agent == null) {
            return Response.success(null);
        }
        AiAgentResponse resp = toResponseWithoutModelName(agent);
        if (StringUtils.hasText(agent.getModelCode())) {
            AiModel model = aiModelService.getByModelCode(agent.getModelCode());
            if (model != null) {
                resp.setModelName(model.getModelName());
            }
        }
        fillKnowledgeBases(resp);
        return Response.success(resp);
    }

    /**
     * 创建智能体。
     *
     * @param req 创建请求
     * @return 新主键
     */
    @PostMapping
    public Response<Long> create(@RequestBody AiAgentCreateRequest req) {
        log.info("API create agent: name={}, sourceAgentId={}, orchestrateMode={}",
                req != null ? req.getAgentName() : null,
                req != null ? req.getSourceAgentId() : null,
                req != null ? req.getOrchestrateMode() : null);
        return Response.success(aiAgentService.create(req));
    }

    /**
     * 更新智能体。
     *
     * @param req 更新请求
     */
    @PutMapping
    public Response<Void> update(@RequestBody AiAgentUpdateRequest req) {
        log.info("API update agent: id={}", req != null ? req.getId() : null);
        aiAgentService.update(req);
        return Response.success();
    }

    /**
     * 启用智能体。
     *
     * @param id 主键
     */
    @PutMapping("/{id}/enable")
    public Response<Void> enable(@PathVariable("id") Long id) {
        log.info("API enable agent: id={}", id);
        aiAgentService.enable(id);
        return Response.success();
    }

    /**
     * 停用智能体。
     *
     * @param id 主键
     */
    @PutMapping("/{id}/disable")
    public Response<Void> disable(@PathVariable("id") Long id) {
        log.info("API disable agent: id={}", id);
        aiAgentService.disable(id);
        return Response.success();
    }

    /**
     * 删除智能体及其知识库绑定。
     *
     * @param id 主键
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("API delete agent: id={}", id);
        aiAgentService.deleteById(id);
        return Response.success();
    }

    /**
     * 全量重绑知识库（列表顺序即优先级）。
     *
     * @param id                  智能体主键
     * @param knowledgeBaseCodes 知识库编码列表
     */
    @PutMapping("/{id}/knowledge-bases")
    public Response<Void> bindKnowledgeBases(@PathVariable("id") Long id, @RequestBody List<String> knowledgeBaseCodes) {
        log.info("API bind KBs: agentId={}, codes={}", id, knowledgeBaseCodes);
        aiAgentService.bindKnowledgeBases(id, knowledgeBaseCodes);
        return Response.success();
    }

    /**
     * Entity → Response（不含 modelName，由调用方填充）。
     *
     * @param s 智能体实体
     * @return 响应 DTO
     */
    private AiAgentResponse toResponseWithoutModelName(AiAgent s) {
        AiAgentResponse r = new AiAgentResponse();
        r.setId(s.getId());
        r.setAgentCode(s.getAgentCode());
        r.setAgentName(s.getAgentName());
        r.setModelCode(s.getModelCode());
        r.setSystemPrompt(s.getSystemPrompt());
        r.setTemperature(s.getTemperature());
        r.setMaxTokens(s.getMaxTokens());
        r.setTopP(s.getTopP());
        r.setMemoryMaxMessages(s.getMemoryMaxMessages());
        r.setEnableMemory(s.getEnableMemory());
        r.setEnableMemoryName(YesNo.labelOf(s.getEnableMemory()));
        r.setResponseFormat(s.getResponseFormat());
        r.setResponseFormatName(org.deepstack.ai.kernel.enums.model.ResponseFormatEnum.labelOf(s.getResponseFormat()));
        r.setResponseSchema(s.getResponseSchema());
        r.setEnabled(s.getEnabled());
        r.setEnabledName(EnabledStatusEnum.labelOf(s.getEnabled()));
        r.setOrchestrateMode(s.getOrchestrateMode());
        r.setOrchestrateModeName(org.deepstack.ai.kernel.enums.agent.OrchestrateModeEnum.labelOf(s.getOrchestrateMode()));
        r.setGraphDefinition(s.getGraphDefinition());
        r.setGraphVersion(s.getGraphVersion());
        r.setPublishedVersion(s.getPublishedVersion());
        r.setQuotaQps(s.getQuotaQps());
        r.setQuotaConcurrency(s.getQuotaConcurrency());
        r.setQuotaDailyTokens(s.getQuotaDailyTokens());
        r.setHitlTimeoutMinutes(s.getHitlTimeoutMinutes());
        r.setTraceMode(s.getTraceMode());
        r.setTraceModeName(org.deepstack.ai.kernel.enums.agent.TraceModeEnum.labelOf(s.getTraceMode()));
        r.setStreamProgress(s.getStreamProgress());
        r.setStreamProgressName(YesNo.labelOf(s.getStreamProgress()));
        r.setCoverUrl(s.getCoverUrl());
        r.setCreateTime(s.getCreateTime());
        r.setUpdateTime(s.getUpdateTime());
        return r;
    }

    /**
     * 批量查询模型显示名。
     *
     * @param modelCodes 模型编码集合
     * @return modelCode → modelName
     */
    private Map<String, String> batchGetModelNames(Set<String> modelCodes) {
        if (modelCodes.isEmpty()) {
            return Map.of();
        }
        return aiModelService.listEnabled().stream()
                .filter(m -> modelCodes.contains(m.getModelCode()))
                .collect(Collectors.toMap(AiModel::getModelCode, AiModel::getModelName, (a, b) -> a));
    }

    /**
     * 填充知识库绑定列表（含名称）。
     *
     * @param resp 智能体响应
     */
    private void fillKnowledgeBases(AiAgentResponse resp) {
        List<String> codes = aiAgentService.getKnowledgeBaseCodes(resp.getId());
        if (codes.isEmpty()) {
            resp.setKnowledgeBases(List.of());
            return;
        }
        // 一次 IN 查名称，避免绑库多时 N+1
        Map<String, String> nameByCode = knowledgeBaseService.listByBaseCodes(codes).stream()
                .filter(kb -> kb.getBaseCode() != null)
                .collect(Collectors.toMap(KnowledgeBase::getBaseCode, KnowledgeBase::getBaseName, (a, b) -> a));
        log.debug("fillKnowledgeBases: agentId={}, codes={}, namesHit={}",
                resp.getId(), codes.size(), nameByCode.size());
        List<AiAgentResponse.KnowledgeBaseBinding> bindings = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            AiAgentResponse.KnowledgeBaseBinding binding = new AiAgentResponse.KnowledgeBaseBinding();
            binding.setBaseCode(code);
            binding.setPriority(i);
            binding.setBaseName(nameByCode.get(code));
            bindings.add(binding);
        }
        resp.setKnowledgeBases(bindings);
    }
}
