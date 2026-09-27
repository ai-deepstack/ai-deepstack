package org.deepstack.ai.knowledge.service.impl;

import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentCreateByKeyRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentCreateRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentPageRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentUpdateRequest;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.model.entity.KnowledgeDocument;
import org.deepstack.ai.knowledge.graph.KnowledgeGraphIndexer;
import org.deepstack.ai.knowledge.mapper.KnowledgeChunkMapper;
import org.deepstack.ai.knowledge.mapper.KnowledgeDocumentMapper;
import org.deepstack.ai.knowledge.service.DocumentProcessor;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import org.deepstack.ai.knowledge.service.KnowledgeDocumentService;
import org.deepstack.ai.kernel.enums.knowledge.DocEmbedStatusEnum;
import org.deepstack.ai.kernel.enums.knowledge.DocGraphStatusEnum;
import org.deepstack.ai.kernel.enums.knowledge.DocParseStatusEnum;
import org.deepstack.ai.kernel.enums.knowledge.DocSourceTypeEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.file.FileInfo;
import org.deepstack.ai.kernel.file.FileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 知识库文档服务实现
 *
 * <p>本类只承担 DB 状态机 + 上传落盘 + 触发异步流水线。
 * 真正的解析（Tika / Jsoup）+ 切片（TokenTextSplitter）+ 向量化（OpenAiEmbeddingModel）
 * 由 {@code DocumentProcessor} 异步执行（下一步实现）。</p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> implements KnowledgeDocumentService {

    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentProcessor documentProcessor;
    private final FileService fileService;
    private final KnowledgeGraphIndexer knowledgeGraphIndexer;

    /** 可选对象存储桶名（空则使用 deepstack.s3.default-bucket） */
    @Value("${deepstack.kb.bucket:#{null}}")
    private String kbBucket;

    /**
     * 分页查询文档（不含 rawContent）。
     *
     * @param req 分页与筛选条件
     * @return 分页结果
     */
    @Override
    public IPage<KnowledgeDocument> page(KnowledgeDocumentPageRequest req) {
        log.info("page: kbId={}, pageNum={}, pageSize={}, sourceType={}, parseStatus={}, embedStatus={}, keyword={}",
                req.getKnowledgeBaseId(), req.getPageNum(), req.getPageSize(),
                req.getSourceType(), req.getParseStatus(), req.getEmbedStatus(), req.getKeyword());
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, req.getKnowledgeBaseId())
                .eq(req.getSourceType() != null,
                        KnowledgeDocument::getSourceType, req.getSourceType())
                .eq(req.getParseStatus() != null,
                        KnowledgeDocument::getParseStatus, req.getParseStatus())
                .eq(req.getEmbedStatus() != null,
                        KnowledgeDocument::getEmbedStatus, req.getEmbedStatus())
                // 列表不返回 rawContent，节省传输
                .select(KnowledgeDocument.class, info -> !"rawContent".equals(info.getProperty()))
                .orderByDesc(KnowledgeDocument::getUpdateTime);

        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(KnowledgeDocument::getTitle, req.getKeyword())
                    .or()
                    .like(KnowledgeDocument::getTags, req.getKeyword()));
        }

        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * 创建 MANUAL / URL 来源文档并触发异步处理。
     *
     * @param req 创建请求
     * @return 新文档 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeDocumentCreateRequest req) {
        log.info("create: kbId={}, sourceType={}, title={}",
                req.getKnowledgeBaseId(), req.getSourceType(), req.getTitle());
        // 校验 KB
        KnowledgeBase kb = knowledgeBaseService.getById(req.getKnowledgeBaseId());
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: id=" + req.getKnowledgeBaseId());
        }

        KnowledgeDocument entity = new KnowledgeDocument();
        entity.setKnowledgeBaseId(req.getKnowledgeBaseId());
        entity.setTitle(req.getTitle());
        entity.setSourceType(req.getSourceType());
        entity.setTags(req.getTags());

        if (req.getSourceType() != null && req.getSourceType() == DocSourceTypeEnum.MANUAL.getCode()) {
            if (!StringUtils.hasText(req.getRawContent())) {
                throw new BusinessException(CommonErrorCode.MISSING_PARAM.getCode(),
                        "MANUAL 来源 rawContent 不能为空");
            }
            entity.setRawContent(req.getRawContent());
            entity.setContentHash(sha256(req.getRawContent()));
            // MANUAL 无需解析，直接 PARSED
            entity.setParseStatus(DocParseStatusEnum.PARSED.getCode());
            entity.setEmbedStatus(DocEmbedStatusEnum.PENDING.getCode());
        } else if (req.getSourceType() != null && req.getSourceType() == DocSourceTypeEnum.URL.getCode()) {
            if (!StringUtils.hasText(req.getSourceUrl())) {
                throw new BusinessException(CommonErrorCode.MISSING_PARAM.getCode(),
                        "URL 来源 sourceUrl 不能为空");
            }
            entity.setSourceUrl(req.getSourceUrl());
            // URL 需异步抓取-解析
            entity.setParseStatus(DocParseStatusEnum.PENDING.getCode());
            entity.setEmbedStatus(DocEmbedStatusEnum.PENDING.getCode());
        } else {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "不支持的来源类型: " + req.getSourceType());
        }

        entity.setChunkCount(0);
        baseMapper.insert(entity);
        log.info("Created knowledge document: id={}, kbId={}, sourceType={}",
                entity.getId(), entity.getKnowledgeBaseId(), entity.getSourceType());

        // 触发异步处理流水线（MANUAL 跳过解析直接 embedding；URL 走完整流水线）
        log.info("create 触发异步处理: docId={}", entity.getId());
        documentProcessor.processAsync(entity.getId());

        return entity.getId();
    }

    /**
     * 上传文件到对象存储并创建 FILE 来源文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param title           标题（可空，默认文件名）
     * @param tags            标签
     * @param file            上传文件
     * @return 新文档 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadFile(Long knowledgeBaseId, String title, String tags, MultipartFile file) {
        log.info("uploadFile: kbId={}, title={}, originalName={}, size={}",
                knowledgeBaseId, title,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CommonErrorCode.MISSING_PARAM.getCode(), "上传文件不能为空");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: id=" + knowledgeBaseId);
        }

        // 上传到 OSS（知识库文档桶）
        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileKey = "kb/" + knowledgeBaseId + "/" + dateDir + "/"
                + UUID.randomUUID().toString().replace("-", "") + "-" + safeName;
        String bucket = resolveKbBucket();

        FileInfo fileInfo;
        try {
            fileInfo = fileService.uploadToBucket(
                    file.getInputStream(), fileKey, file.getContentType(),
                    file.getSize(), bucket);
        } catch (IOException e) {
            log.error("File upload to OSS failed: kbId={}, name={}", knowledgeBaseId, originalName, e);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(),
                    "文件上传失败: " + e.getMessage());
        }

        KnowledgeDocument entity = new KnowledgeDocument();
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setTitle(StringUtils.hasText(title) ? title : safeName);
        entity.setSourceType(DocSourceTypeEnum.FILE.getCode());
        entity.setFileName(originalName);
        entity.setFileKey(fileKey);
        entity.setFilePath(fileInfo.getFileUrl());
        entity.setFileSize(file.getSize());
        entity.setMimeType(file.getContentType());
        entity.setTags(tags);
        entity.setParseStatus(DocParseStatusEnum.PENDING.getCode());
        entity.setEmbedStatus(DocEmbedStatusEnum.PENDING.getCode());
        entity.setChunkCount(0);
        baseMapper.insert(entity);

        log.info("Uploaded knowledge document to OSS: id={}, kbId={}, fileKey={}, size={}",
                entity.getId(), knowledgeBaseId, fileKey, file.getSize());

        // 触发异步解析 + embedding 流水线
        log.info("uploadFile 触发异步处理: docId={}", entity.getId());
        documentProcessor.processAsync(entity.getId());

        return entity.getId();
    }

    /**
     * 按已有 fileKey 创建 FILE 来源文档（跳过上传）。
     *
     * @param req 创建请求
     * @return 新文档 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createByKey(KnowledgeDocumentCreateByKeyRequest req) {
        log.info("createByKey: kbId={}, fileKey={}, fileName={}",
                req.getKnowledgeBaseId(), req.getFileKey(), req.getFileName());
        KnowledgeBase kb = knowledgeBaseService.getById(req.getKnowledgeBaseId());
        if (kb == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: id=" + req.getKnowledgeBaseId());
        }
        if (req.getFileKey() == null || req.getFileKey().isBlank()) {
            throw new BusinessException(CommonErrorCode.MISSING_PARAM.getCode(), "fileKey 不能为空");
        }

        String originalName = req.getFileName();
        String safeName = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");

        KnowledgeDocument entity = new KnowledgeDocument();
        entity.setKnowledgeBaseId(req.getKnowledgeBaseId());
        entity.setTitle(StringUtils.hasText(req.getTitle()) ? req.getTitle() : safeName);
        entity.setSourceType(DocSourceTypeEnum.FILE.getCode());
        entity.setFileName(originalName);
        entity.setFileKey(req.getFileKey());
        entity.setFilePath(req.getFilePath());
        entity.setFileSize(req.getFileSize());
        entity.setMimeType(req.getMimeType());
        entity.setTags(req.getTags());
        entity.setParseStatus(DocParseStatusEnum.PENDING.getCode());
        entity.setEmbedStatus(DocEmbedStatusEnum.PENDING.getCode());
        entity.setChunkCount(0);
        baseMapper.insert(entity);

        log.info("Created knowledge document by fileKey: id={}, kbId={}, fileKey={}",
                entity.getId(), req.getKnowledgeBaseId(), req.getFileKey());

        // 触发异步解析 + embedding 流水线
        log.info("createByKey 触发异步处理: docId={}", entity.getId());
        documentProcessor.processAsync(entity.getId());

        return entity.getId();
    }

    /**
     * 解析知识库文档桶名：优先 {@code deepstack.kb.bucket}，否则用文件服务默认桶。
     */
    private String resolveKbBucket() {
        if (StringUtils.hasText(kbBucket)) {
            return kbBucket;
        }
        // 回退到文件服务默认桶
        return null;
    }

    /**
     * 更新文档元数据；若修改 MANUAL rawContent 则清空分片并重新 embedding。
     *
     * @param req 更新请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(KnowledgeDocumentUpdateRequest req) {
        log.info("update: id={}", req.getId());
        KnowledgeDocument entity = baseMapper.selectById(req.getId());
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "文档不存在: id=" + req.getId());
        }

        boolean contentChanged = false;
        if (req.getTitle() != null) entity.setTitle(req.getTitle());
        if (req.getTags() != null) entity.setTags(req.getTags());
        if (req.getRawContent() != null) {
            if (entity.getSourceType() == null
                    || entity.getSourceType() != DocSourceTypeEnum.MANUAL.getCode()) {
                throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                        "仅 MANUAL 类型支持修改 rawContent");
            }
            entity.setRawContent(req.getRawContent());
            entity.setContentHash(sha256(req.getRawContent()));
            entity.setParseStatus(DocParseStatusEnum.PARSED.getCode());
            entity.setEmbedStatus(DocEmbedStatusEnum.PENDING.getCode());
            entity.setErrorMsg(null);
            contentChanged = true;
        }

        baseMapper.updateById(entity);

        if (contentChanged) {
            // 清空旧 chunks，等待重新 embedding
            int deleted = knowledgeChunkMapper.deleteByDocumentId(entity.getId());
            log.info("Cleared {} old chunks for re-embedding: docId={}", deleted, entity.getId());
            log.info("update 触发异步处理: docId={}", entity.getId());
            documentProcessor.processAsync(entity.getId());
        }

        log.info("Updated knowledge document: id={}", req.getId());
    }

    /**
     * 重置文档并触发重新 embedding。
     *
     * @param id 文档 ID
     */
    @Override
    public void reembed(Long id) {
        log.info("reembed: id={}", id);
        KnowledgeDocument entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "文档不存在: id=" + id);
        }
        KnowledgeBase kb = knowledgeBaseService.getById(entity.getKnowledgeBaseId());
        // 重嵌前清图，避免旧 Chunk 节点残留
        if (kb != null) {
            try {
                knowledgeGraphIndexer.deleteDocumentGraph(kb, id);
            } catch (Exception e) {
                log.warn("reembed 清图失败（继续）: docId={}, err={}", id, e.getMessage());
            }
        }
        // 清空旧 chunks
        int deleted = knowledgeChunkMapper.deleteByDocumentId(id);
        // 重置状态
        entity.setEmbedStatus(DocEmbedStatusEnum.PENDING.getCode());
        entity.setGraphStatus(DocGraphStatusEnum.PENDING.getCode());
        entity.setGraphError(null);
        entity.setErrorMsg(null);
        entity.setChunkCount(0);
        baseMapper.updateById(entity);
        log.info("Reset for re-embedding: docId={}, clearedChunks={}", id, deleted);
        log.info("reembed 触发异步处理: docId={}", id);
        documentProcessor.processAsync(id);
    }

    /** 重新抽取并写入知识图谱。 */
    @Override
    public void regraph(Long id) {
        log.info("regraph: id={}", id);
        knowledgeGraphIndexer.regraph(id);
    }

    /**
     * 删除文档及其分片（分片物理删除，文档逻辑删除）。
     *
     * @param id 文档 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        log.info("deleteById: id={}", id);
        KnowledgeDocument entity = baseMapper.selectById(id);
        if (entity != null) {
            KnowledgeBase kb = knowledgeBaseService.getById(entity.getKnowledgeBaseId());
            if (kb != null) {
                try {
                    knowledgeGraphIndexer.deleteDocumentGraph(kb, id);
                } catch (Exception e) {
                    log.warn("deleteById 清图失败（继续）: docId={}, err={}", id, e.getMessage());
                }
            }
        }
        // 物理删除分片（chunks 不走逻辑删除，避免向量索引膨胀）
        int chunks = knowledgeChunkMapper.deleteByDocumentId(id);
        // 文档逻辑删除
        baseMapper.deleteById(id);
        log.info("Deleted knowledge document: id={}, removedChunks={}", id, chunks);
    }

    // ===== private =====

    /** 计算内容 SHA-256 十六进制摘要。 */
    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            throw new BusinessException(CommonErrorCode.SYSTEM_ERROR.getCode(),
                    "SHA-256 算法不可用");
        }
    }
}
