package org.deepstack.ai.knowledge.service;

import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentCreateByKeyRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentCreateRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentPageRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentUpdateRequest;
import org.deepstack.ai.knowledge.model.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档服务
 *
 * <p>状态机：</p>
 * <ul>
 *   <li>parse_status: PENDING → PARSING → PARSED / FAILED</li>
 *   <li>embed_status: PENDING → EMBEDDING → EMBEDDED / FAILED</li>
 * </ul>
 *
 */
public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    // 解析/向量化状态码见 kernel DocParseStatusEnum / DocEmbedStatusEnum

    // ===== CRUD =====

    /**
     * 分页查询文档（列表不返回 rawContent）
     */
    IPage<KnowledgeDocument> page(KnowledgeDocumentPageRequest req);

    /**
     * 新建文档（手工录入 / URL 抓取）
     * <p>
     * MANUAL：rawContent 直接存库，立即触发 embedding 异步任务。<br/>
     * URL：源 URL 存库，状态 PENDING；触发抓取-解析-embedding 异步流水线。
     * </p>
     *
     * @return 新记录 id
     */
    Long create(KnowledgeDocumentCreateRequest req);

    /**
     * 文件上传 + 解析 + embedding 异步流水线
     *
     * @param knowledgeBaseId 知识库 id
     * @param title           文档标题（缺省取文件名）
     * @param tags            逗号分隔标签
     * @param file            上传文件
     * @return 新记录 id
     */
    Long uploadFile(Long knowledgeBaseId, String title, String tags, MultipartFile file);

    /**
     * 按 OSS fileKey 创建文档（文件已上传后调用）
     * <p>
     * AI 服务不接收 multipart 文件，只记录 fileKey，
     * 异步处理器通过 FileService 按 key 从 OSS 下载并用 Tika 解析。
     * </p>
     *
     * @param req 包含 fileKey、fileName、fileSize、mimeType 等元数据
     * @return 新记录 id
     */
    Long createByKey(KnowledgeDocumentCreateByKeyRequest req);

    /**
     * 修改文档（标题 / 标签 / 正文）。
     * 修改 rawContent 后清空旧 chunks 并重新触发 embedding。
     */
    void update(KnowledgeDocumentUpdateRequest req);

    /**
     * 重新触发 embedding（手工重试 FAILED 文档）
     */
    void reembed(Long id);

    /**
     * 仅重跑写图（不重 embed）。enable_graph 关闭时标记 SKIPPED。
     */
    void regraph(Long id);

    /**
     * 逻辑删除文档（同时级联物理删除 chunks）
     */
    void deleteById(Long id);
}
