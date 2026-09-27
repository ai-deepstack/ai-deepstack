package org.deepstack.ai.knowledge.service;

import org.deepstack.ai.knowledge.model.dto.request.KnowledgeChunkPageRequest;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 知识库分片服务
 * <p>
 * 分片仅由 {@link KnowledgeDocumentService} 切片流水线写入，
 * 分页查询与维度内删除；检索能力由本服务对外暴露。
 * </p>
 *
 */
public interface KnowledgeChunkService extends IService<KnowledgeChunk> {

    // ===== 检索方法 =====

    /**
     * 向量相似度检索
     *
     * @param knowledgeBaseId 知识库 id
     * @param queryText       查询文本（先 embed 再查）
     * @param topK            返回条数
     * @return 分片列表（按距离升序）
     */
    List<KnowledgeChunk> searchByVector(Long knowledgeBaseId, String queryText, int topK);

    /**
     * 全文检索（ts_vector）
     *
     * @param knowledgeBaseId 知识库 id
     * @param queryText       查询文本
     * @param topK            返回条数
     * @return 分片列表（按 ts_rank 降序）
     */
    List<KnowledgeChunk> searchByFullText(Long knowledgeBaseId, String queryText, int topK);

    /**
     * 混合检索：向量 + 全文 RRF 融合
     *
     * @param knowledgeBaseId       知识库 id
     * @param queryText             查询文本
     * @param topK                  最终返回条数
     * @param similarityThreshold   相似度阈值（cosine 距离上限，超过则丢弃；null 不过滤）
     * @return 融合后分片列表
     */
    List<KnowledgeChunk> hybridSearch(Long knowledgeBaseId, String queryText, int topK, Double similarityThreshold);

    // ===== CRUD =====

    /**
     * 分页查询分片
     */
    IPage<KnowledgeChunk> page(KnowledgeChunkPageRequest req);

    /**
     * 按 documentId 物理删除分片（重建 embedding 时用）
     */
    int deleteByDocumentId(Long documentId);
}
