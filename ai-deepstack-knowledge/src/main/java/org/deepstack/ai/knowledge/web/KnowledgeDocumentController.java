package org.deepstack.ai.knowledge.web;

import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentCreateByKeyRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentCreateRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentPageRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeDocumentUpdateRequest;
import org.deepstack.ai.knowledge.model.dto.response.KnowledgeDocumentResponse;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.knowledge.model.entity.KnowledgeDocument;
import org.deepstack.ai.knowledge.service.KnowledgeDocumentService;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.enums.knowledge.DocEmbedStatusEnum;
import org.deepstack.ai.kernel.enums.knowledge.DocGraphStatusEnum;
import org.deepstack.ai.kernel.enums.knowledge.DocParseStatusEnum;
import org.deepstack.ai.kernel.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档管理 API（客户端）
 * <p>
 * 单体统一 API。
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/docs")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    /**
     * 分页查询知识库文档列表（不含正文）。
     *
     * @param req 分页与筛选条件
     * @return 文档分页结果
     */
    @GetMapping("/page")
    public Response<PageInfo<KnowledgeDocumentResponse>> page(KnowledgeDocumentPageRequest req) {
        log.info("分页查询知识文档: knowledgeBaseId={}", req != null ? req.getKnowledgeBaseId() : null);
        return Response.success(PageInfoUtils.of(knowledgeDocumentService.page(req), this::toResponseList));
    }

    /**
     * 按主键查询文档详情（含 rawContent）。
     *
     * @param id 文档 id
     * @return 文档详情，不存在时为 null
     */
    @GetMapping("/{id}")
    public Response<KnowledgeDocumentResponse> get(@PathVariable("id") Long id) {
        log.info("查询知识文档详情: id={}", id);
        KnowledgeDocument entity = knowledgeDocumentService.getById(id);
        return Response.success(entity != null ? toResponseDetail(entity) : null);
    }

    /**
     * 创建文本文档（直接提交内容）。
     *
     * @param req 创建请求（知识库 id、标题、内容等）
     */
    @PostMapping
    public Response<Void> create(@RequestBody KnowledgeDocumentCreateRequest req) {
        log.info("创建知识文档: knowledgeBaseId={}", req != null ? req.getKnowledgeBaseId() : null);
        knowledgeDocumentService.create(req);
        return Response.success();
    }

    /**
     * 上传文件并创建文档。
     *
     * @param knowledgeBaseId 所属知识库 id
     * @param title           可选标题
     * @param tags            可选标签
     * @param file            上传文件
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Void> upload(
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam("file") MultipartFile file) {
        log.info("上传知识文档: knowledgeBaseId={}, fileName={}",
                knowledgeBaseId, file != null ? file.getOriginalFilename() : null);
        knowledgeDocumentService.uploadFile(knowledgeBaseId, title, tags, file);
        return Response.success();
    }

    /**
     * 按 OSS fileKey 创建文档（文件已上传后调用）。
     *
     * @param req 含 knowledgeBaseId、fileKey 等
     * @return 新建文档 id
     */
    @PostMapping("/create-by-key")
    public Response<Long> createByKey(@RequestBody KnowledgeDocumentCreateByKeyRequest req) {
        log.info("按 fileKey 创建知识文档: knowledgeBaseId={}", req != null ? req.getKnowledgeBaseId() : null);
        Long id = knowledgeDocumentService.createByKey(req);
        return Response.success(id);
    }

    /**
     * 更新文档元数据。
     *
     * @param req 更新请求（含 id）
     */
    @PutMapping
    public Response<Void> update(@RequestBody KnowledgeDocumentUpdateRequest req) {
        log.info("更新知识文档: id={}", req != null ? req.getId() : null);
        knowledgeDocumentService.update(req);
        return Response.success();
    }

    /**
     * 重新向量化文档（触发重新分片/嵌入）。
     *
     * @param id 文档 id
     */
    @PutMapping("/{id}/reembed")
    public Response<Void> reembed(@PathVariable("id") Long id) {
        log.info("重新向量化文档: id={}", id);
        knowledgeDocumentService.reembed(id);
        return Response.success();
    }

    /**
     * 仅重跑写图（不重 embed）。
     *
     * @param id 文档 id
     */
    @PutMapping("/{id}/regraph")
    public Response<Void> regraph(@PathVariable("id") Long id) {
        log.info("重新写图: id={}", id);
        knowledgeDocumentService.regraph(id);
        return Response.success();
    }

    /**
     * 删除文档及其分片。
     *
     * @param id 文档 id
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("删除知识文档: id={}", id);
        knowledgeDocumentService.deleteById(id);
        return Response.success();
    }

    /**
     * 列表映射（不含 rawContent）
     */
    private KnowledgeDocumentResponse toResponseList(KnowledgeDocument d) {
        return toResponse(d, false);
    }

    /**
     * 详情映射（含 rawContent）
     */
    private KnowledgeDocumentResponse toResponseDetail(KnowledgeDocument d) {
        return toResponse(d, true);
    }

    /**
     * 实体转响应 DTO。
     *
     * @param d              文档实体
     * @param includeContent 是否包含 rawContent
     */
    private KnowledgeDocumentResponse toResponse(KnowledgeDocument d, boolean includeContent) {
        KnowledgeDocumentResponse r = new KnowledgeDocumentResponse();
        r.setId(d.getId());
        r.setKnowledgeBaseId(d.getKnowledgeBaseId());
        r.setTitle(d.getTitle());
        r.setSourceType(d.getSourceType());
        r.setSourceTypeName(org.deepstack.ai.kernel.enums.knowledge.DocSourceTypeEnum.labelOf(d.getSourceType()));
        r.setSourceUrl(d.getSourceUrl());
        r.setFileName(d.getFileName());
        r.setFileSize(d.getFileSize());
        r.setMimeType(d.getMimeType());
        if (includeContent) {
            r.setRawContent(d.getRawContent());
        }
        r.setTags(d.getTags());
        r.setParseStatus(d.getParseStatus());
        r.setParseStatusName(DocParseStatusEnum.labelOf(d.getParseStatus()));
        r.setEmbedStatus(d.getEmbedStatus());
        r.setEmbedStatusName(DocEmbedStatusEnum.labelOf(d.getEmbedStatus()));
        r.setGraphStatus(d.getGraphStatus());
        r.setGraphStatusName(DocGraphStatusEnum.labelOf(d.getGraphStatus()));
        r.setGraphError(d.getGraphError());
        r.setChunkCount(d.getChunkCount());
        r.setErrorMsg(d.getErrorMsg());
        r.setCreateTime(d.getCreateTime());
        r.setUpdateTime(d.getUpdateTime());
        return r;
    }
}
