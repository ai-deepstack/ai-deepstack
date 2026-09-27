package org.deepstack.ai.knowledge.mapper;

import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库分片 Mapper
 * <p>
 * 向量检索走自定义 SQL（pgvector cosine 距离 + ts_vector 全文 + RRF 融合），
 * BaseMapper 仅承载基础 CRUD。
 * </p>
 *
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 向量相似度检索（cosine 距离）
     * <p>
     * pgvector {@code <=>} 操作符返回 cosine 距离（0=完全相似，2=完全相反）。
     * 相似度 = 1 - 距离。
     * </p>
     *
     * @param knowledgeBaseId 知识库 ID
     * @param embeddingVector 查询向量（字符串格式 {@code [0.1,0.2,...]}）
     * @param topK            返回条数
     * @return 分片列表（按距离升序）
     */
    @Select("SELECT c.* FROM knowledge_chunk c " +
            "WHERE c.knowledge_base_id = #{knowledgeBaseId} " +
            "  AND c.is_del = 0 " +
            "  AND c.embedding IS NOT NULL " +
            "ORDER BY c.embedding <=> #{embeddingVector}::vector " +
            "LIMIT #{topK}")
    List<KnowledgeChunk> searchByVector(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                        @Param("embeddingVector") String embeddingVector,
                                        @Param("topK") int topK);

    /**
     * 向量相似度检索，可选按 cosine 距离上限过滤。
     * <p>
     * {@code maxDistance} 为 null 时不过滤；否则仅保留 {@code embedding <=> query &lt;= maxDistance} 的分片。
     * cosine 距离：0=完全相似，2=完全相反；常见阈值约 0.3~0.7。
     * </p>
     */
    @Select("<script>" +
            "SELECT c.* FROM knowledge_chunk c " +
            "WHERE c.knowledge_base_id = #{knowledgeBaseId} " +
            "  AND c.is_del = 0 " +
            "  AND c.embedding IS NOT NULL " +
            "<if test='maxDistance != null'>" +
            "  AND (c.embedding &lt;=&gt; #{embeddingVector}::vector) &lt;= #{maxDistance} " +
            "</if>" +
            "ORDER BY c.embedding &lt;=&gt; #{embeddingVector}::vector " +
            "LIMIT #{topK}" +
            "</script>")
    List<KnowledgeChunk> searchByVectorWithThreshold(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                     @Param("embeddingVector") String embeddingVector,
                                                     @Param("topK") int topK,
                                                     @Param("maxDistance") Double maxDistance);

    /**
     * 全文检索（ts_vector，simple 分析器）
     *
     * @param knowledgeBaseId 知识库 ID
     * @param queryText       查询文本
     * @param topK            返回条数
     * @return 分片列表（按 ts_rank 降序）
     */
    @Select("SELECT c.*, ts_rank(c.content_tsv, plainto_tsquery('simple', #{queryText})) AS rank " +
            "FROM knowledge_chunk c " +
            "WHERE c.knowledge_base_id = #{knowledgeBaseId} " +
            "  AND c.is_del = 0 " +
            "  AND c.content_tsv @@ plainto_tsquery('simple', #{queryText}) " +
            "ORDER BY rank DESC " +
            "LIMIT #{topK}")
    List<KnowledgeChunk> searchByFullText(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                          @Param("queryText") String queryText,
                                          @Param("topK") int topK);

    /**
     * 按 document_id 物理删除分片（重建 embedding 时用）
     *
     * @param documentId 文档 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM knowledge_chunk WHERE document_id = #{documentId}")
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
