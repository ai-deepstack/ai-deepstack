package org.deepstack.ai.knowledge.service.impl;


import org.deepstack.ai.infra.llm.EmbeddingClientFactory;
import org.deepstack.ai.knowledge.graph.KnowledgeGraphIndexer;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import org.deepstack.ai.knowledge.model.entity.KnowledgeDocument;
import org.deepstack.ai.knowledge.mapper.KnowledgeChunkMapper;
import org.deepstack.ai.knowledge.mapper.KnowledgeDocumentMapper;
import org.deepstack.ai.knowledge.service.DocumentProcessor;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import org.deepstack.ai.knowledge.service.KnowledgeChunkService;
import org.deepstack.ai.kernel.enums.knowledge.DocEmbedStatusEnum;
import org.deepstack.ai.kernel.enums.knowledge.DocParseStatusEnum;
import org.deepstack.ai.kernel.enums.knowledge.DocSourceTypeEnum;
import java.util.Objects;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.file.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 文档异步处理器实现：抓取 → Tika/Jsoup 解析 → TokenTextSplitter 切片 →
 * OpenAiEmbeddingModel 向量化 → 写库 + 状态推进。
 * <p>
 * 幂等：每次进入会清空旧 chunks 再重新处理，状态机保证可观测。
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessorImpl implements DocumentProcessor {

    /** URL 抓取超时 */
    private static final int URL_TIMEOUT_MS = 15000;

    /** 单批 embedding 最大文本数（智谱 / OpenAI 通常支持 64 ~ 100） */
    private static final int EMBED_BATCH_SIZE = 32;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeChunkService knowledgeChunkService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingClientFactory embeddingClientFactory;
    private final FileService fileService;
    private final KnowledgeGraphIndexer knowledgeGraphIndexer;

    /**
     * 知识库文档桶名（与 KnowledgeDocumentServiceImpl 共用同一配置）
     */
    @Value("${deepstack.kb.bucket:#{null}}")
    private String kbBucket;

    private final Tika tika = new Tika();

    /**
     * 异步处理文档：解析 → 切片 → embedding → 写库，失败时标记 FAILED。
     *
     * @param documentId 文档主键
     */
    @Override
    @Async("knowledgeProcessExecutor")
    public void processAsync(Long documentId) {
        log.info("DocumentProcessor start: docId={}", documentId);
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("DocumentProcessor: document not found, docId={}", documentId);
            return;
        }
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKnowledgeBaseId());
        if (kb == null) {
            updateFailed(doc, "knowledge_base 不存在或已删除");
            return;
        }

        try {
            // 1. 解析（按需）
            String rawContent = ensureRawContent(doc);
            if (rawContent == null || rawContent.trim().isEmpty()) {
                updateFailed(doc, "解析后内容为空");
                return;
            }

            // 2. 切片
            doc.setEmbedStatus(DocEmbedStatusEnum.EMBEDDING.getCode());
            doc.setErrorMsg(null);
            knowledgeDocumentMapper.updateById(doc);

            List<String> chunks = splitText(rawContent, kb.getChunkSize(), kb.getChunkOverlap());
            if (chunks.isEmpty()) {
                updateFailed(doc, "切片结果为空");
                return;
            }
            log.info("Document split done: docId={}, chunks={}", documentId, chunks.size());

            // 3. 清空旧 chunks（幂等）
            knowledgeChunkMapper.deleteByDocumentId(documentId);

            // 4. 批量 embedding
            OpenAiEmbeddingModel embeddingModel =
                    embeddingClientFactory.getEmbeddingModel(requireEmbeddingModelCode(kb));
            int saved = embedAndSave(doc, kb, chunks, embeddingModel);

            // 5. 状态推进
            doc.setEmbedStatus(DocEmbedStatusEnum.EMBEDDED.getCode());
            doc.setChunkCount(saved);
            doc.setErrorMsg(null);
            knowledgeDocumentMapper.updateById(doc);
            log.info("DocumentProcessor done: docId={}, savedChunks={}", documentId, saved);

            // 6. 写图（与 embed 同异步线程；失败只影响 graph_status，不回滚向量）
            try {
                // 重新加载最新 doc（含 chunkCount）；indexer 内部再查 chunks
                KnowledgeDocument fresh = knowledgeDocumentMapper.selectById(documentId);
                if (fresh != null) {
                    log.info("DocumentProcessor 开始写图: docId={}, kb={}", documentId, kb.getBaseCode());
                    knowledgeGraphIndexer.indexDocument(fresh, kb);
                }
            } catch (Exception ge) {
                log.error("DocumentProcessor 写图异常（不影响 embed）: docId={}, err={}",
                        documentId, ge.getMessage(), ge);
            }
        } catch (Exception e) {
            log.error("DocumentProcessor failed: docId={}", documentId, e);
            updateFailed(doc, e.getClass().getSimpleName() + ": " + safeMsg(e.getMessage()));
        }
    }

    // ===== 解析 =====

    /**
     * 确保 doc.rawContent 已就位：
     * <ul>
     *   <li>MANUAL：本来就有，直接返回；</li>
     *   <li>FILE：用 Tika 解析磁盘文件，写回 raw_content + content_hash；</li>
     *   <li>URL：用 Jsoup 抓取 + 提取正文，写回 raw_content + content_hash。</li>
     * </ul>
     */
    private String ensureRawContent(KnowledgeDocument doc) throws IOException, TikaException {
        if (doc.getSourceType() != null
                && doc.getSourceType() == DocSourceTypeEnum.MANUAL.getCode()) {
            log.debug("ensureRawContent MANUAL: docId={}, contentLen={}",
                    doc.getId(), doc.getRawContent() != null ? doc.getRawContent().length() : 0);
            return doc.getRawContent();
        }

        // 状态置为 PARSING
        log.info("ensureRawContent parsing: docId={}, sourceType={}", doc.getId(), doc.getSourceType());
        doc.setParseStatus(DocParseStatusEnum.PARSING.getCode());
        doc.setErrorMsg(null);
        knowledgeDocumentMapper.updateById(doc);

        String parsed;
        if (doc.getSourceType() != null
                && doc.getSourceType() == DocSourceTypeEnum.FILE.getCode()) {
            parsed = parseFileFromOss(doc);
        } else if (doc.getSourceType() != null
                && doc.getSourceType() == DocSourceTypeEnum.URL.getCode()) {
            parsed = fetchAndParseUrl(doc.getSourceUrl());
        } else {
            throw new IllegalStateException("不支持的 sourceType: " + doc.getSourceType());
        }

        doc.setRawContent(parsed);
        doc.setContentHash(parsed == null ? null : sha256(parsed));
        doc.setParseStatus(DocParseStatusEnum.PARSED.getCode());
        knowledgeDocumentMapper.updateById(doc);
        log.info("ensureRawContent done: docId={}, parsedLen={}",
                doc.getId(), parsed != null ? parsed.length() : 0);
        return parsed;
    }

    /**
     * 从 OSS 下载文件并用 Tika 解析。
     * <p>
     * 优先使用 fileKey（新上传的文档），回退到 filePath（旧文档的本地路径，兼容存量）。
     * </p>
     */
    private String parseFileFromOss(KnowledgeDocument doc) throws IOException, TikaException {
        String fileKey = doc.getFileKey();
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalStateException("fileKey 为空，无法从 OSS 下载。docId=" + doc.getId());
        }
        String bucket = resolveKbBucket();
        log.info("parseFileFromOss: docId={}, bucket={}, fileKey={}", doc.getId(), bucket, fileKey);
        try (InputStream in = fileService.download(bucket, fileKey)) {
            return tika.parseToString(in);
        }
    }

    /**
     * 解析知识库文档桶名：优先 {@code deepstack.kb.bucket}，否则用文件服务默认桶。
     */
    private String resolveKbBucket() {
        if (kbBucket != null && !kbBucket.isBlank()) {
            return kbBucket;
        }
        // null 表示使用文件服务默认桶
        return null;
    }

    /**
     * 抓取 URL HTML，优先提取 &lt;article&gt; 正文，否则取 body 文本。
     *
     * @param url 源地址
     * @return 纯文本正文
     */
    private String fetchAndParseUrl(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("sourceUrl 为空");
        }
        log.info("fetchAndParseUrl: url={}", url);
        URL u = URI.create(url).toURL();
        URLConnection conn = u.openConnection();
        conn.setConnectTimeout(URL_TIMEOUT_MS);
        conn.setReadTimeout(URL_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (compatible; ai-deepstack/1.0)");
        try (InputStream in = conn.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            String html = new String(bytes, StandardCharsets.UTF_8);
            Document jsoupDoc = Jsoup.parse(html, url);
            // 优先取 <article>，否则取 <body>
            String articleText = jsoupDoc.select("article").text();
            String text = articleText.isBlank() ? jsoupDoc.body().text() : articleText;
            log.debug("fetchAndParseUrl done: url={}, textLen={}", url, text != null ? text.length() : 0);
            return text;
        }
    }

    // ===== 切片 =====

    /**
     * 用 {@link TokenTextSplitter} 按 token 数切片。
     * <p>
     * Spring AI 2.0 的 TokenTextSplitter 不直接暴露 overlap 参数（按标点截断），
     * 这里 overlap 仅作记录，实际由切片器自行控制语义边界。
     * </p>
     */
    private List<String> splitText(String text, Integer chunkSize, Integer chunkOverlap) {
        int size = chunkSize == null || chunkSize <= 0 ? 800 : chunkSize;
        log.debug("splitText: chunkSize={}, overlapHint={}, textLen={}",
                size, chunkOverlap, text != null ? text.length() : 0);
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(size)
                .build();
        org.springframework.ai.document.Document doc =
                new org.springframework.ai.document.Document(text);
        List<org.springframework.ai.document.Document> docs = splitter.split(doc);
        List<String> result = new ArrayList<>(docs.size());
        for (org.springframework.ai.document.Document d : docs) {
            String t = d.getText();
            if (t != null && !t.isBlank()) {
                result.add(t);
            }
        }
        return result;
    }

    // ===== embedding =====

    /**
     * 分批调用 embedding 模型并批量写入 knowledge_chunk（saveBatch，避免逐条 insert）。
     *
     * @return 成功落库的分片数
     */
    private int embedAndSave(KnowledgeDocument doc, KnowledgeBase kb,
                             List<String> chunks, OpenAiEmbeddingModel embeddingModel) {
        int totalSaved = 0;
        log.info("embedAndSave start: docId={}, kbId={}, chunks={}, batchSize={}",
                doc.getId(), kb.getId(), chunks.size(), EMBED_BATCH_SIZE);
        for (int batchStart = 0; batchStart < chunks.size(); batchStart += EMBED_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + EMBED_BATCH_SIZE, chunks.size());
            List<String> batch = chunks.subList(batchStart, batchEnd);
            log.debug("embedAndSave batch: docId={}, range=[{}, {})", doc.getId(), batchStart, batchEnd);

            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));
            if (response == null || response.getResults() == null
                    || response.getResults().size() != batch.size()) {
                throw new IllegalStateException("embedding 返回数量与输入不匹配: expect="
                        + batch.size() + ", actual="
                        + (response == null || response.getResults() == null
                            ? 0 : response.getResults().size()));
            }

            List<KnowledgeChunk> toSave = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                String content = batch.get(i);
                float[] vector = response.getResults().get(i).getOutput();

                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeBaseId(kb.getId());
                chunk.setDocumentId(doc.getId());
                chunk.setChunkIndex(batchStart + i);
                chunk.setContent(content);
                chunk.setTokenCount(estimateTokens(content));
                chunk.setEmbedding(floatArrayToVectorString(vector));
                chunk.setMetadata(null);
                toSave.add(chunk);
            }
            // MyBatis-Plus 批量插入（JDBC batch），比逐条 insert 少往返
            boolean ok = knowledgeChunkService.saveBatch(toSave, toSave.size());
            if (!ok) {
                throw new IllegalStateException("chunk saveBatch 失败: docId=" + doc.getId()
                        + ", range=[" + batchStart + ", " + batchEnd + ")");
            }
            totalSaved += toSave.size();
            log.info("embedAndSave batch saved: docId={}, range=[{}, {}), size={}",
                    doc.getId(), batchStart, batchEnd, toSave.size());
        }
        log.info("embedAndSave done: docId={}, saved={}", doc.getId(), totalSaved);
        return totalSaved;
    }

    /**
     * 粗略估算 token 数（中文 ~1.5 char/token，英文 ~4 char/token，取中庸 3）
     */
    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 3);
    }

    /** float[] → pgvector 文本字面量，如 {@code [0.1,0.2,...]}。 */
    private String floatArrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    // ===== 工具方法 =====

    /** 按当前阶段将文档标记为 FAILED 并写入 errorMsg。 */
    private void updateFailed(KnowledgeDocument doc, String msg) {
        log.warn("DocumentProcessor updateFailed: docId={}, parseStatus={}, embedStatus={}, msg={}",
                doc.getId(), doc.getParseStatus(), doc.getEmbedStatus(), msg);
        // 解析阶段失败 → parseStatus=FAILED；embedding 阶段失败 → embedStatus=FAILED
        if (Objects.equals(doc.getParseStatus(), DocParseStatusEnum.PARSING.getCode())) {
            doc.setParseStatus(DocParseStatusEnum.FAILED.getCode());
        }
        if (Objects.equals(doc.getEmbedStatus(), DocEmbedStatusEnum.EMBEDDING.getCode())) {
            doc.setEmbedStatus(DocEmbedStatusEnum.FAILED.getCode());
        } else if (!Objects.equals(doc.getEmbedStatus(), DocEmbedStatusEnum.EMBEDDED.getCode())) {
            doc.setEmbedStatus(DocEmbedStatusEnum.FAILED.getCode());
        }
        doc.setErrorMsg(msg);
        knowledgeDocumentMapper.updateById(doc);
    }

    /** 截断异常消息，避免 error_msg 列过长。 */
    private String safeMsg(String msg) {
        if (msg == null) return "";
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    /**
     * 知识库绑定的 embedding 模型编码。
     *
     * @param kb 知识库
     * @return modelCode
     */
    private String requireEmbeddingModelCode(KnowledgeBase kb) {
        if (!org.springframework.util.StringUtils.hasText(kb.getEmbeddingModelCode())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "知识库未配置 embedding 模型: kbId=" + kb.getId());
        }
        return kb.getEmbeddingModelCode();
    }

    /** 计算内容 SHA-256 十六进制摘要。 */
    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
