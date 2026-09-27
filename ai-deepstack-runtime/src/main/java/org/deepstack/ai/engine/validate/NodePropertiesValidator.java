package org.deepstack.ai.engine.validate;

import java.util.*;

/**
 * 节点属性的编译期校验器。
 * <p>
 * 每种节点类型声明其必填属性。编译期若缺失，立即抛出
 * {@link IllegalArgumentException} 并给出明确信息，避免运行时静默失败。
 * </p>
 *
 */
public final class NodePropertiesValidator {

    private NodePropertiesValidator() {}

    /**
     * 各节点类型的必填属性。
     * Key：节点类型编码（工作流定义 JSON 中使用的 type）。
     * Value：必填属性名列表。
     */
    private static final Map<String, List<String>> REQUIRED_PROPERTIES = Map.ofEntries(
            Map.entry("intent-node", List.of("modelCode", "categories")),
            Map.entry("llm-node", List.of("modelCode")),
            Map.entry("rag-node", List.of("knowledgeBaseCodes")),
            Map.entry("tool-node", List.of("toolId")),
            Map.entry("condition-node", List.of("conditionExpression")),
            Map.entry("memory-node", List.of("mode")),
            Map.entry("assign-node", List.of("variableName"))
    );

    /**
     * 校验指定节点类型的全部必填属性是否存在。
     *
     * @param nodeId     节点 ID（用于错误信息）
     * @param type       节点类型编码
     * @param properties 工作流定义中的节点属性 map
     * @throws IllegalArgumentException 任一必填属性缺失或为 null 时抛出
     */
    public static void validate(String nodeId, String type, Map<String, Object> properties) {
        // start-node / end-node 无必填属性
        if ("start-node".equals(type) || "end-node".equals(type)) {
            return;
        }

        List<String> required = REQUIRED_PROPERTIES.get(type);
        if (required == null) {
            // 未知类型 —— 交由 createNodeAction 处理
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String prop : required) {
            Object val = properties.get(prop);
            if (val == null) {
                missing.add(prop);
            } else if (val instanceof String s && s.isBlank()) {
                missing.add(prop);
            } else if (val instanceof Collection<?> c && c.isEmpty()) {
                missing.add(prop);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Node '" + nodeId + "' (type=" + type + ") missing required properties: "
                            + String.join(", ", missing)
                            + ". Required: " + String.join(", ", required));
        }
    }
}
