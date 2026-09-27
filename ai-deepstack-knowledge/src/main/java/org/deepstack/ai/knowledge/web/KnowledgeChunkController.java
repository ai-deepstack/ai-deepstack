package org.deepstack.ai.knowledge.web;


import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeChunkPageRequest;
import org.deepstack.ai.knowledge.model.dto.response.KnowledgeChunkResponse;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.knowledge.model.entity.KnowledgeChunk;
import org.deepstack.ai.knowledge.service.KnowledgeChunkService;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库分片管理 API（客户端）
 * <p>
 * 分片为只读（自动生成），仅提供分页查询与单条删除。
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/chunks")
@RequiredArgsConstructor
public class KnowledgeChunkController {

    private final KnowledgeChunkService knowledgeChunkService;

    /**
     * 分页查询知识分片列表。
     *
     * @param req 分页与筛选条件（知识库、文档等）
     * @return 分片分页结果（不含 embedding 向量）
     */
    @GetMapping("/page")
    public Response<PageInfo<KnowledgeChunkResponse>> page(KnowledgeChunkPageRequest req) {
        log.info("分页查询知识分片: documentId={}",
                req != null ? req.getDocumentId() : null);
        return Response.success(PageInfoUtils.of(knowledgeChunkService.page(req), this::toResponse));
    }

    /**
     * 删除单条知识分片。
     *
     * @param id 分片 id
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("删除知识分片: id={}", id);
        knowledgeChunkService.removeById(id);
        return Response.success();
    }

    /**
     * Entity → Response 映射。
     * <p>
     * embedding 向量字段不返回（避免传输 1024 维浮点数据）。
     * hasEmbedding 为业务字段，由 embedding 是否非空推导。
     * </p>
     */
    private KnowledgeChunkResponse toResponse(KnowledgeChunk c) {
        KnowledgeChunkResponse r = new KnowledgeChunkResponse();
        r.setId(c.getId());
        r.setKnowledgeBaseId(c.getKnowledgeBaseId());
        r.setDocumentId(c.getDocumentId());
        r.setChunkIndex(c.getChunkIndex());
        r.setContent(c.getContent());
        r.setTokenCount(c.getTokenCount());
        r.setHasEmbedding(YesNo.codeOf(c.getEmbedding() != null && !c.getEmbedding().isEmpty()));
        r.setHasEmbeddingName(YesNo.labelOf(r.getHasEmbedding()));
        r.setMetadata(c.getMetadata());
        r.setCreateTime(c.getCreateTime());
        return r;
    }
}
