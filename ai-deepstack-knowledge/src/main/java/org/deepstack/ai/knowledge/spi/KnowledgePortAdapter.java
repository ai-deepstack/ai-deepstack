package org.deepstack.ai.knowledge.spi;

import org.deepstack.ai.knowledge.model.dto.internal.KnowledgeRetrieveResult;
import org.deepstack.ai.knowledge.service.KnowledgeRetrieveFacade;
import org.deepstack.ai.runtime.spi.KnowledgePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link KnowledgePort} 适配：委托 {@link KnowledgeRetrieveFacade}（单库 / 多库并行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgePortAdapter implements KnowledgePort {

    private final KnowledgeRetrieveFacade knowledgeRetrieveFacade;

    /**
     * 单库检索：走 Facade → hybridSearch（含可选图谱扩边）。
     */
    @Override
    public List<String> retrieve(String baseCode, String query, int topK, double similarityThreshold) {
        log.info("KnowledgePort.retrieve: baseCode={}, topK={}, threshold={}, queryLen={}",
                baseCode, topK, similarityThreshold, query != null ? query.length() : 0);
        KnowledgeRetrieveResult part = knowledgeRetrieveFacade.retrieveOne(
                baseCode, query, topK, similarityThreshold);
        if (part == null || part.getTexts() == null || part.getTexts().isEmpty()) {
            log.info("KnowledgePort.retrieve 无命中: baseCode={}, failed={}, err={}",
                    baseCode,
                    part != null && part.isFailed(),
                    part != null ? part.getErrorMessage() : null);
            return List.of();
        }
        log.info("KnowledgePort.retrieve 完成: baseCode={}, hit={}, costMs={}",
                baseCode, part.getTexts().size(), part.getCostMs());
        return List.copyOf(part.getTexts());
    }

    /**
     * 多库并行检索：Semaphore 限流 + 按 baseCodes 顺序扁平化。
     */
    @Override
    public List<String> retrieveAll(List<String> baseCodes, String query,
                                    int topK, double similarityThreshold) {
        log.info("KnowledgePort.retrieveAll: kbCount={}, topK={}, threshold={}, queryLen={}",
                baseCodes != null ? baseCodes.size() : 0,
                topK, similarityThreshold, query != null ? query.length() : 0);
        List<KnowledgeRetrieveResult> parts = knowledgeRetrieveFacade.retrieveAll(
                baseCodes, query, topK, similarityThreshold);
        List<String> texts = knowledgeRetrieveFacade.flattenTexts(parts);
        log.info("KnowledgePort.retrieveAll 完成: kbCount={}, flatHit={}",
                baseCodes != null ? baseCodes.size() : 0, texts.size());
        return texts;
    }
}
