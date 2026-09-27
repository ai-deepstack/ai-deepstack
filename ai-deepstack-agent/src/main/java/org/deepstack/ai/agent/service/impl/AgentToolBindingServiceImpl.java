package org.deepstack.ai.agent.service.impl;

import org.deepstack.ai.agent.mapper.AiAgentToolMapper;
import org.deepstack.ai.agent.model.dto.request.AiAgentToolBindRequest;
import org.deepstack.ai.agent.model.dto.response.AiAgentToolResponse;
import org.deepstack.ai.agent.model.entity.AiAgentTool;
import org.deepstack.ai.agent.service.AgentToolBindingService;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.tool.model.entity.AiTool;
import org.deepstack.ai.tool.service.AiToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * {@link AgentToolBindingService} 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolBindingServiceImpl implements AgentToolBindingService {

    private final AiAgentToolMapper aiAgentToolMapper;
    private final AiToolService aiToolService;
    private final ToolPort toolPort;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AiAgentToolResponse> listToolsByAgent(Long agentId) {
        log.debug("listToolsByAgent: agentId={}", agentId);
        List<AiAgentTool> bindings = aiAgentToolMapper.selectList(
                new LambdaQueryWrapper<AiAgentTool>()
                        .eq(AiAgentTool::getAgentId, agentId)
                        .orderByAsc(AiAgentTool::getPriority)
        );
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> toolIds = bindings.stream().map(AiAgentTool::getToolId).toList();
        List<AiTool> tools = aiToolService.listByIds(toolIds);
        HashMap<Long, AiTool> toolMap = new HashMap<>();
        for (AiTool t : tools) {
            toolMap.put(t.getId(), t);
        }

        List<AiAgentToolResponse> result = new ArrayList<>(bindings.size());
        for (AiAgentTool b : bindings) {
            AiAgentToolResponse r = new AiAgentToolResponse();
            r.setId(b.getId());
            r.setAgentId(b.getAgentId());
            r.setToolId(b.getToolId());
            r.setPriority(b.getPriority());
            r.setEnabled(b.getEnabled());
            r.setEnabledName(org.deepstack.ai.kernel.enums.common.EnabledStatusEnum.labelOf(b.getEnabled()));
            AiTool t = toolMap.get(b.getToolId());
            if (t != null) {
                r.setToolCode(t.getToolCode());
                r.setToolName(t.getToolName());
            }
            result.add(r);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>先删后插；变更后 {@link ToolPort#evictAll()} 清理解析缓存。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTools(AiAgentToolBindRequest req) {
        aiAgentToolMapper.deleteByAgentId(req.getAgentId());
        if (req.getToolIds() != null && !req.getToolIds().isEmpty()) {
            for (int i = 0; i < req.getToolIds().size(); i++) {
                AiAgentTool rel = new AiAgentTool();
                rel.setAgentId(req.getAgentId());
                rel.setToolId(req.getToolIds().get(i));
                rel.setPriority(i);
                rel.setEnabled(YesNo.YES.getCode());
                aiAgentToolMapper.insert(rel);
            }
        }
        toolPort.evictAll();
        log.info("bound tools to agent: agentId={}, toolIds={}", req.getAgentId(), req.getToolIds());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> listEnabledToolCodes(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        List<String> codes = aiAgentToolMapper.selectEnabledToolCodesByAgentId(agentId);
        return codes != null ? codes : List.of();
    }
}
