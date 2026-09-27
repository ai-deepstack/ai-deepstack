package org.deepstack.ai.agent.graph;

import org.deepstack.ai.engine.tool.AgentTool;
import org.deepstack.ai.agent.model.dto.response.NodeTypeResponse;
import org.deepstack.ai.agent.model.entity.AgentNodeType;
import org.deepstack.ai.agent.model.entity.AiIntent;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.agent.service.AgentNodeTypeService;
import org.deepstack.ai.agent.service.AiIntentService;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 编排侧节点类型注册中心（画布面板）。
 * <p>
 * 从数据库加载节点类型定义，动态注入选项数据（模型/知识库/工具），
 * 输出前端可直接使用的 {@link NodeTypeResponse} 列表。
 * </p>
 *
 * <h3>propertyFields JSON 中的 optionsSource 字段</h3>
 * <ul>
 *   <li>{@code CHAT_MODEL} — 查询启用的 CHAT 模型列表</li>
 *   <li>{@code KB}        — 查询启用的知识库列表</li>
 *   <li>{@code TOOL}      — 查询注册的 AgentTool 列表</li>
 *   <li>{@code INTENT}    — 查询当前租户启用的意图字典</li>
 *   <li>无 / {@code MANUAL} — 使用 propertyFields 内嵌的 options 字段</li>
 * </ul>
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeTypeRegistry {

    private final AgentNodeTypeService agentNodeTypeService;
    private final AiModelService aiModelService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiIntentService aiIntentService;
    /** 可选：classpath 上注册的 AgentTool（不依赖 runtime/tool 模块） */
    private final List<AgentTool> agentTools;

    // ===== 动态选项数据源 =====

    private static final String OPT_CHAT_MODEL = "CHAT_MODEL";
    private static final String OPT_KB = "KB";
    private static final String OPT_TOOL = "TOOL";
    private static final String OPT_INTENT = "INTENT";

    /**
     * 构建所有启用的节点类型（含动态选项注入），供 /node-types 接口使用。
     *
     * @return 前端可用的节点类型定义列表
     */
    public List<NodeTypeResponse> buildAll() {
        List<AgentNodeType> entities = agentNodeTypeService.listEnabled();
        log.debug("NodeTypeRegistry: building node types, enabledCount={}", entities.size());

        // 预加载动态选项数据
        Map<String, List<Map<String, Object>>> optionCache = new HashMap<>();
        optionCache.put(OPT_CHAT_MODEL, buildModelOptions(org.deepstack.ai.kernel.enums.model.ModelTypeEnum.CHAT.getCode()));
        optionCache.put(OPT_KB, buildKnowledgeBaseOptions());
        optionCache.put(OPT_TOOL, buildToolOptions());
        optionCache.put(OPT_INTENT, buildIntentOptions());

        List<NodeTypeResponse> result = new ArrayList<>(entities.size());
        for (AgentNodeType entity : entities) {
            try {
                result.add(toResponse(entity, optionCache));
            } catch (Exception e) {
                log.warn("NodeTypeRegistry: failed to parse node type typeCode={}, error={}",
                        entity.getTypeCode(), e.getMessage());
            }
        }
        log.info("NodeTypeRegistry: built {} node types", result.size());
        return result;
    }

    /**
     * 刷新底层缓存（节点类型 + 选项数据都会重新加载）。
     */
    public void refreshCache() {
        log.info("NodeTypeRegistry: refreshing node type cache");
        agentNodeTypeService.refreshCache();
    }

    // ===== 转换 =====

    /**
     * 实体 → 前端 NodeTypeResponse，并注入动态 options。
     */
    private NodeTypeResponse toResponse(AgentNodeType entity,
                                        Map<String, List<Map<String, Object>>> optionCache) {
        NodeTypeResponse r = new NodeTypeResponse();
        r.setType(entity.getTypeCode());
        r.setLabel(entity.getLabel());
        r.setIcon(entity.getIcon());
        r.setCategory(entity.getCategory());
        r.setMaxInPorts(entity.getMaxInPorts() != null ? entity.getMaxInPorts() : -1);
        r.setMaxOutPorts(entity.getMaxOutPorts() != null ? entity.getMaxOutPorts() : -1);

        // 解析 defaultProperties JSON
        r.setDefaultProperties(parseJsonMap(entity.getDefaultProps()));

        // 解析 propertyFields JSON 并注入动态选项
        List<NodeTypeResponse.PropertyField> fields = parsePropertyFields(entity.getPropertyFields(), optionCache);
        r.setPropertyFields(fields);

        return r;
    }

    /**
     * 解析 propertyFields JSON，并按 optionsSource 注入动态选项。
     */
    private List<NodeTypeResponse.PropertyField> parsePropertyFields(String json,
                                                                      Map<String, List<Map<String, Object>>> optionCache) {
        if (json == null || json.isEmpty()) return List.of();

        try {
            List<Map<String, Object>> rawList = JSON.parseObject(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            List<NodeTypeResponse.PropertyField> result = new ArrayList<>(rawList.size());
            for (Map<String, Object> raw : rawList) {
                NodeTypeResponse.PropertyField f = new NodeTypeResponse.PropertyField();
                f.setKey((String) raw.get("key"));
                f.setLabel((String) raw.get("label"));
                f.setWidget((String) raw.get("widget"));
                f.setRequired(Boolean.TRUE.equals(raw.get("required")));
                f.setDefaultValue(raw.get("defaultValue"));
                f.setPlaceholder((String) raw.get("placeholder"));

                // 动态选项注入
                String optionsSource = (String) raw.get("optionsSource");
                if (optionsSource != null && optionCache.containsKey(optionsSource)) {
                    f.setOptions(optionCache.get(optionsSource));
                } else {
                    // 若存在内联 options 则使用
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> inline = (List<Map<String, Object>>) raw.get("options");
                    f.setOptions(inline);
                }

                result.add(f);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse propertyFields JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /** 解析 JSON 对象为 Map；失败返回空 Map。 */
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("NodeTypeRegistry: failed to parse defaultProps JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    // ===== 动态选项构建（从 Controller 迁入） =====

    /** 构建启用模型下拉选项（label/value）。 */
    private List<Map<String, Object>> buildModelOptions(Integer modelType) {
        log.debug("buildModelOptions: modelType={}", modelType);
        List<AiModel> models = (modelType != null)
                ? aiModelService.listEnabledByType(modelType)
                : aiModelService.listEnabled();
        List<Map<String, Object>> options = new ArrayList<>();
        for (AiModel m : models) {
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("label", m.getModelName() + " (" + m.getProvider() + ")");
            opt.put("value", m.getModelCode());
            options.add(opt);
        }
        return options;
    }

    /** 构建启用知识库下拉选项（label=名称, value=baseCode）。 */
    private List<Map<String, Object>> buildKnowledgeBaseOptions() {
        List<KnowledgeBase> kbs = knowledgeBaseService.listEnabled();
        log.debug("buildKnowledgeBaseOptions: count={}", kbs.size());
        List<Map<String, Object>> options = new ArrayList<>();
        for (KnowledgeBase kb : kbs) {
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("label", kb.getBaseName());
            opt.put("value", kb.getBaseCode());
            options.add(opt);
        }
        return options;
    }

    /** 构建已注册 AgentTool 下拉选项。 */
    private List<Map<String, Object>> buildToolOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        List<AgentTool> tools = agentTools != null ? agentTools : List.of();
        for (AgentTool t : tools) {
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("label", t.getName());
            opt.put("value", t.getCode());
            opt.put("description", t.getDescription());
            options.add(opt);
        }
        log.debug("buildToolOptions: count={}", options.size());
        return options;
    }

    /** 构建租户意图字典下拉选项（当前固定默认租户 0）。 */
    private List<Map<String, Object>> buildIntentOptions() {
        List<AiIntent> intents = aiIntentService.listEnabledByTenant(AiIntentService.DEFAULT_TENANT_ID);
        List<Map<String, Object>> options = new ArrayList<>();
        for (AiIntent intent : intents) {
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("label", intent.getIntentName() + " (" + intent.getIntentCode() + ")");
            opt.put("value", intent.getIntentCode());
            options.add(opt);
        }
        log.debug("buildIntentOptions: count={}", options.size());
        return options;
    }
}
