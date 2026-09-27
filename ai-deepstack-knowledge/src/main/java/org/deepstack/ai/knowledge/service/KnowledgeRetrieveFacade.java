package org.deepstack.ai.knowledge.service;

import org.deepstack.ai.knowledge.model.dto.internal.KnowledgeRetrieveResult;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 知识召回 Facade：单库 hybrid（含可选图扩边）+ 多库并行限流。
 * <p>
 * CHAT / {@code KnowledgePort} 统一走本类，禁止业务侧直接串行调 {@code hybridSearch}。
 * 多库任务一律提交到 {@code knowledgeRecallExecutor}，用 Semaphore 限制
 * {@code knowledge.recall.max-kb-concurrency}；单库超时读
 * {@code knowledge.recall.timeout-ms}。
 * </p>
 */
@Slf4j
@Service
public class KnowledgeRetrieveFacade {

    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_MAX_KB_CONCURRENCY = 4;
    private static final int DEFAULT_TIMEOUT_MS = 3000;

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeChunkService knowledgeChunkService;
    private final SysConfigPort sysConfigPort;
    /**
     * 多库编排线程池：只做「按库提交 + 等待」。
     * 库内 hybrid/expand 在 {@code knowledgeRecallExecutor}，禁止同池嵌套。
     */
    private final Executor knowledgeMultiKbExecutor;

    public KnowledgeRetrieveFacade(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeChunkService knowledgeChunkService,
                                   SysConfigPort sysConfigPort,
                                   @Qualifier("knowledgeMultiKbExecutor") Executor knowledgeMultiKbExecutor) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeChunkService = knowledgeChunkService;
        this.sysConfigPort = sysConfigPort;
        this.knowledgeMultiKbExecutor = knowledgeMultiKbExecutor;
    }

    /**
     * 单库召回：解析库配置（topK / threshold）后走 hybridSearch。
     *
     * @param baseCode           知识库编码
     * @param query              查询文本
     * @param defaultTopK        库未配置 topK 时的默认值；null → 5
     * @param defaultThreshold   库未配置阈值时的默认值；可为 null（不设阈值）
     * @return 单库结果；库不存在时 failed=true、texts 空
     */
    public KnowledgeRetrieveResult retrieveOne(String baseCode, String query,
                                               Integer defaultTopK, Double defaultThreshold) {
        KnowledgeRetrieveResult out = new KnowledgeRetrieveResult();
        out.setBaseCode(baseCode);
        long t0 = System.currentTimeMillis();

        if (!StringUtils.hasText(baseCode)) {
            out.setFailed(true);
            out.setErrorMessage("baseCode 为空");
            out.setCostMs(System.currentTimeMillis() - t0);
            log.warn("retrieveOne 跳过: baseCode 为空");
            return out;
        }
        if (!StringUtils.hasText(query)) {
            out.setFailed(true);
            out.setErrorMessage("query 为空");
            out.setCostMs(System.currentTimeMillis() - t0);
            log.warn("retrieveOne 跳过: query 为空, baseCode={}", baseCode);
            return out;
        }

        try {
            KnowledgeBase kb = knowledgeBaseService.getByBaseCode(baseCode.trim());
            if (kb == null) {
                out.setFailed(true);
                out.setErrorMessage("知识库不存在或已禁用");
                out.setCostMs(System.currentTimeMillis() - t0);
                log.warn("retrieveOne: kb 不存在 baseCode={}", baseCode);
                return out;
            }
            out.setBaseName(kb.getBaseName());
            int topK = kb.getTopK() != null ? kb.getTopK()
                    : (defaultTopK != null && defaultTopK > 0 ? defaultTopK : DEFAULT_TOP_K);
            Double threshold = kb.getSimilarityThreshold() != null
                    ? kb.getSimilarityThreshold().doubleValue()
                    : defaultThreshold;

            log.info("retrieveOne 开始: baseCode={}, topK={}, threshold={}, queryLen={}",
                    baseCode, topK, threshold, query.length());
            List<KnowledgeChunk> chunks = knowledgeChunkService.hybridSearch(
                    kb.getId(), query, topK, threshold);
            List<String> texts = new ArrayList<>();
            if (chunks != null) {
                for (KnowledgeChunk c : chunks) {
                    if (c != null && StringUtils.hasText(c.getContent())) {
                        texts.add(c.getContent());
                    }
                }
            }
            out.setTexts(texts);
            out.setCostMs(System.currentTimeMillis() - t0);
            log.info("retrieveOne 完成: baseCode={}, hit={}, costMs={}",
                    baseCode, texts.size(), out.getCostMs());
            return out;
        } catch (Exception e) {
            out.setFailed(true);
            out.setErrorMessage(e.getMessage());
            out.setCostMs(System.currentTimeMillis() - t0);
            log.error("retrieveOne 失败: baseCode={}, err={}, costMs={}",
                    baseCode, e.getMessage(), out.getCostMs(), e);
            return out;
        }
    }

    /**
     * 多库并行召回：每个 baseCode 一个任务，Semaphore 限流，按输入顺序 join。
     * <p>
     * 单库超时/失败 → 该库空上下文，不影响其他库；最终列表长度与 {@code baseCodes} 一致。
     * </p>
     *
     * @param baseCodes          有序知识库编码（请求覆盖或智能体绑定）
     * @param query              查询文本
     * @param defaultTopK        库未配 topK 时默认
     * @param defaultThreshold   库未配阈值时默认
     * @return 与 baseCodes 同序的结果列表
     */
    public List<KnowledgeRetrieveResult> retrieveAll(List<String> baseCodes, String query,
                                                     Integer defaultTopK, Double defaultThreshold) {
        if (baseCodes == null || baseCodes.isEmpty()) {
            log.info("retrieveAll 跳过: baseCodes 为空");
            return List.of();
        }
        if (!StringUtils.hasText(query)) {
            log.warn("retrieveAll 跳过: query 为空, kbCount={}", baseCodes.size());
            return List.of();
        }

        int maxConc = Math.max(1, sysConfigPort.getInt(
                SysConfigKeys.KNOWLEDGE_RECALL_MAX_KB_CONCURRENCY, DEFAULT_MAX_KB_CONCURRENCY));
        // 编排池 max=8，并发不宜超过该上限，避免排队过深
        if (maxConc > 8) {
            log.warn("retrieveAll: max-kb-concurrency={} 超过编排池上限，截断为 8", maxConc);
            maxConc = 8;
        }
        int timeoutMs = Math.max(500, sysConfigPort.getInt(
                SysConfigKeys.KNOWLEDGE_RECALL_TIMEOUT_MS, DEFAULT_TIMEOUT_MS));
        int batches = (baseCodes.size() + maxConc - 1) / maxConc;
        long totalTimeoutMs = (long) timeoutMs * batches;
        log.info("retrieveAll 启动: kbCount={}, maxConcurrency={}, perKbTimeoutMs={}, totalTimeoutMs={}, queryLen={}",
                baseCodes.size(), maxConc, timeoutMs, totalTimeoutMs, query.length());

        Semaphore lim = new Semaphore(maxConc);
        long t0 = System.currentTimeMillis();
        long deadline = t0 + totalTimeoutMs;

        // 按输入下标提交，保证 join 后顺序稳定
        List<CompletableFuture<KnowledgeRetrieveResult>> futures = new ArrayList<>(baseCodes.size());
        for (int i = 0; i < baseCodes.size(); i++) {
            final String code = baseCodes.get(i);
            final int index = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                lim.acquireUninterruptibly();
                try {
                    log.debug("retrieveAll 获取许可: index={}, baseCode={}, availablePermits={}",
                            index, code, lim.availablePermits());
                    return retrieveOne(code, query, defaultTopK, defaultThreshold);
                } finally {
                    lim.release();
                }
            }, knowledgeMultiKbExecutor));
        }

        List<KnowledgeRetrieveResult> ordered = new ArrayList<>(baseCodes.size());
        int ok = 0;
        int fail = 0;
        for (int i = 0; i < futures.size(); i++) {
            String code = baseCodes.get(i);
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) {
                fail++;
                futures.get(i).cancel(true);
                KnowledgeRetrieveResult timedOut = new KnowledgeRetrieveResult();
                timedOut.setBaseCode(code);
                timedOut.setFailed(true);
                timedOut.setErrorMessage("多库总超时 totalTimeoutMs=" + totalTimeoutMs);
                timedOut.setCostMs(totalTimeoutMs);
                ordered.add(timedOut);
                log.warn("retrieveAll 总超时跳过: index={}, baseCode={}, totalTimeoutMs={}",
                        i, code, totalTimeoutMs);
                continue;
            }
            try {
                KnowledgeRetrieveResult part = futures.get(i).get(remain, TimeUnit.MILLISECONDS);
                ordered.add(part);
                if (part.isFailed()) {
                    fail++;
                } else {
                    ok++;
                }
            } catch (TimeoutException te) {
                fail++;
                futures.get(i).cancel(true);
                KnowledgeRetrieveResult timedOut = new KnowledgeRetrieveResult();
                timedOut.setBaseCode(code);
                timedOut.setFailed(true);
                timedOut.setErrorMessage("多库总超时 totalTimeoutMs=" + totalTimeoutMs);
                timedOut.setCostMs(System.currentTimeMillis() - t0);
                ordered.add(timedOut);
                log.warn("retrieveAll 总超时: index={}, baseCode={}, totalTimeoutMs={}",
                        i, code, totalTimeoutMs);
            } catch (Exception e) {
                fail++;
                KnowledgeRetrieveResult err = new KnowledgeRetrieveResult();
                err.setBaseCode(code);
                err.setFailed(true);
                err.setErrorMessage(e.getMessage());
                ordered.add(err);
                log.warn("retrieveAll 单库异常: index={}, baseCode={}, err={}",
                        i, code, e.getMessage());
            }
        }

        log.info("retrieveAll 完成: kbCount={}, ok={}, fail={}, totalCostMs={}",
                baseCodes.size(), ok, fail, System.currentTimeMillis() - t0);
        return ordered;
    }

    /**
     * 将多库结果拼成注入 system prompt 的知识上下文（保持库顺序）。
     *
     * @param parts {@link #retrieveAll} 的有序结果
     * @return 拼接文本；全部无命中时返回 null
     */
    public String formatPromptContext(List<KnowledgeRetrieveResult> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        List<String> contextParts = new ArrayList<>();
        for (KnowledgeRetrieveResult part : parts) {
            if (part == null || part.getTexts() == null || part.getTexts().isEmpty()) {
                continue;
            }
            String name = StringUtils.hasText(part.getBaseName())
                    ? part.getBaseName() : part.getBaseCode();
            StringBuilder sb = new StringBuilder();
            sb.append("--- 知识库: ").append(name)
                    .append(" (").append(part.getBaseCode()).append(") ---\n");
            for (int i = 0; i < part.getTexts().size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                        .append(part.getTexts().get(i)).append("\n");
            }
            contextParts.add(sb.toString());
        }
        if (contextParts.isEmpty()) {
            log.info("formatPromptContext: 无有效命中");
            return null;
        }
        log.debug("formatPromptContext: sections={}", contextParts.size());
        return String.join("\n", contextParts);
    }

    /**
     * 扁平化文本块列表（按库顺序），供 {@code KnowledgePort.retrieveAll} / rag-node 使用。
     */
    public List<String> flattenTexts(List<KnowledgeRetrieveResult> parts) {
        if (parts == null || parts.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> texts = new ArrayList<>();
        for (KnowledgeRetrieveResult part : parts) {
            if (part == null || part.getTexts() == null) {
                continue;
            }
            texts.addAll(part.getTexts());
        }
        return texts;
    }
}
