package org.deepstack.ai.knowledge.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.kernel.enums.model.ModelTypeEnum;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import org.deepstack.ai.runtime.spi.ModelPort;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库建图：用 CHAT 大模型从 chunk 文本抽取实体与关系（JSON）。
 * <p>
 * 模型解析顺序：库级 {@code graphModelCode} → sys_config
 * {@link SysConfigKeys#KNOWLEDGE_GRAPH_CHAT_MODEL_CODE} → 平台第一条启用 CHAT。
 * </p>
 */
@Slf4j
@Component
public class KnowledgeGraphExtractor {

    private static final String SYSTEM_PROMPT = """
            你是知识图谱抽取助手。只输出合法 JSON，不要 markdown，不要解释。
            格式严格为：
            {"entities":[{"name":"实体名","type":"Person|Org|Concept|Event|Other"}],\
            "relations":[{"from":"实体名","to":"实体名","type":"RELATED_TO"}]}
            type 缺省时用 Concept / RELATED_TO。实体名用原文中文或英文专名，勿臆造。
            """;

    private final ObjectProvider<ModelPort> modelPortProvider;
    private final SysConfigPort sysConfigPort;
    private final ObjectProvider<AiModelService> aiModelServiceProvider;

    public KnowledgeGraphExtractor(ObjectProvider<ModelPort> modelPortProvider,
                                   SysConfigPort sysConfigPort,
                                   ObjectProvider<AiModelService> aiModelServiceProvider) {
        this.modelPortProvider = modelPortProvider;
        this.sysConfigPort = sysConfigPort;
        this.aiModelServiceProvider = aiModelServiceProvider;
    }

    /**
     * 解析本库建图所用 CHAT 模型编码；无可用模型时返回 null。
     */
    public String resolveModelCode(KnowledgeBase kb) {
        if (kb != null && StringUtils.hasText(kb.getGraphModelCode())) {
            log.debug("建图模型来自知识库: kb={}, model={}", kb.getBaseCode(), kb.getGraphModelCode());
            return kb.getGraphModelCode().trim();
        }
        String cfg = sysConfigPort.getString(SysConfigKeys.KNOWLEDGE_GRAPH_CHAT_MODEL_CODE);
        if (StringUtils.hasText(cfg)) {
            log.debug("建图模型来自 sys_config: model={}", cfg);
            return cfg.trim();
        }
        AiModelService svc = aiModelServiceProvider.getIfAvailable();
        if (svc != null) {
            List<AiModel> chats = svc.listEnabledByType(ModelTypeEnum.CHAT.getCode());
            if (chats != null && !chats.isEmpty() && StringUtils.hasText(chats.get(0).getModelCode())) {
                String code = chats.get(0).getModelCode();
                log.info("建图模型回落平台首个启用 CHAT: model={}", code);
                return code;
            }
        }
        log.warn("建图无可用 CHAT 模型: kb={}", kb != null ? kb.getBaseCode() : null);
        return null;
    }

    /**
     * 按配置批大小对 chunks 调用 LLM，合并实体/关系。
     *
     * @param kb     知识库（取模型）
     * @param chunks 已落库分片
     * @return 抽取结果；无模型或空输入时 empty
     */
    public KnowledgeGraphExtractResult extract(KnowledgeBase kb, List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.info("抽取跳过: chunks 为空, kb={}", kb != null ? kb.getBaseCode() : null);
            return KnowledgeGraphExtractResult.empty();
        }
        String modelCode = resolveModelCode(kb);
        if (!StringUtils.hasText(modelCode)) {
            throw new IllegalStateException("无可用图谱抽取 CHAT 模型");
        }
        ModelPort modelPort = modelPortProvider.getIfAvailable();
        if (modelPort == null) {
            throw new IllegalStateException("ModelPort 未装配，无法抽取图谱");
        }

        int batchSize = Math.max(1, sysConfigPort.getInt(
                SysConfigKeys.KNOWLEDGE_GRAPH_MAX_CHUNKS_PER_EXTRACT, 8));
        log.info("开始图谱抽取: kb={}, model={}, chunks={}, batchSize={}",
                kb.getBaseCode(), modelCode, chunks.size(), batchSize);

        ChatModel chatModel = modelPort.getChatModel(modelCode);
        List<KnowledgeGraphExtractResult> parts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<KnowledgeChunk> batch = chunks.subList(i, end);
            log.info("抽取批次: kb={}, range=[{}, {}), size={}",
                    kb.getBaseCode(), i, end, batch.size());
            parts.add(extractBatch(chatModel, modelCode, batch));
        }
        KnowledgeGraphExtractResult merged = KnowledgeGraphExtractResult.merge(parts);
        log.info("图谱抽取完成: kb={}, entities={}, relations={}",
                kb.getBaseCode(), merged.getEntities().size(), merged.getRelations().size());
        return merged;
    }

    /** 批量抽取实体与关系。 */
    private KnowledgeGraphExtractResult extractBatch(ChatModel chatModel, String modelCode,
                                                     List<KnowledgeChunk> batch) {
        StringBuilder user = new StringBuilder();
        user.append("请从下列文本块抽取实体与关系，只输出 JSON：\n\n");
        for (int i = 0; i < batch.size(); i++) {
            KnowledgeChunk c = batch.get(i);
            user.append("--- chunk#").append(i).append(" id=").append(c.getId()).append(" ---\n");
            String content = c.getContent() == null ? "" : c.getContent();
            // 单块过长截断，避免超上下文
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "…";
            }
            user.append(content).append("\n\n");
        }

        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(user.toString())
            )));
            String raw = response != null && response.getResult() != null
                    && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText()
                    : null;
            log.debug("抽取原始响应: model={}, truncated={}",
                    modelCode, truncate(raw, 800));
            if (!StringUtils.hasText(raw)) {
                log.warn("抽取返回空文本: model={}, batchSize={}", modelCode, batch.size());
                return KnowledgeGraphExtractResult.empty();
            }
            return parseJson(raw);
        } catch (Exception e) {
            log.error("抽取调用失败: model={}, batchSize={}, err={}",
                    modelCode, batch.size(), e.getMessage(), e);
            throw new IllegalStateException("图谱抽取失败: " + e.getMessage(), e);
        }
    }

    KnowledgeGraphExtractResult parseJson(String raw) {
        String json = extractJsonObject(raw);
        try {
            JSONObject root = JSON.parseObject(json);
            List<KnowledgeGraphExtractResult.Entity> entities = new ArrayList<>();
            List<KnowledgeGraphExtractResult.Relation> relations = new ArrayList<>();
            JSONArray ents = root.getJSONArray("entities");
            if (ents != null) {
                for (int i = 0; i < ents.size(); i++) {
                    JSONObject n = ents.getJSONObject(i);
                    if (n == null) {
                        continue;
                    }
                    String name = text(n, "name");
                    if (!StringUtils.hasText(name)) {
                        continue;
                    }
                    String type = text(n, "type");
                    if (!StringUtils.hasText(type)) {
                        type = "Concept";
                    }
                    entities.add(new KnowledgeGraphExtractResult.Entity(name.trim(), type.trim()));
                }
            }
            JSONArray rels = root.getJSONArray("relations");
            if (rels != null) {
                for (int i = 0; i < rels.size(); i++) {
                    JSONObject n = rels.getJSONObject(i);
                    if (n == null) {
                        continue;
                    }
                    String from = text(n, "from");
                    String to = text(n, "to");
                    if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
                        continue;
                    }
                    String type = text(n, "type");
                    if (!StringUtils.hasText(type)) {
                        type = "RELATED_TO";
                    }
                    // 边类型仅允许字母数字下划线（AGE label）
                    type = type.trim().replaceAll("[^A-Za-z0-9_]", "_");
                    if (type.isEmpty()) {
                        type = "RELATED_TO";
                    }
                    relations.add(new KnowledgeGraphExtractResult.Relation(
                            from.trim(), to.trim(), type));
                }
            }
            log.debug("解析抽取 JSON: entities={}, relations={}", entities.size(), relations.size());
            return new KnowledgeGraphExtractResult(entities, relations);
        } catch (Exception e) {
            log.error("解析抽取 JSON 失败: err={}, rawTruncated={}",
                    e.getMessage(), truncate(raw, 500));
            throw new IllegalStateException("解析图谱抽取 JSON 失败: " + e.getMessage(), e);
        }
    }

    /** 读取 JSON 字段文本。 */
    private static String text(JSONObject n, String field) {
        if (n == null || !n.containsKey(field) || n.get(field) == null) {
            return null;
        }
        String t = n.getString(field);
        return t;
    }

    /** 从模型输出中截取第一个 JSON 对象。 */
    private static String extractJsonObject(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                s = s.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    /** 按最大长度截断文本。 */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…(len=" + s.length() + ")";
    }
}
