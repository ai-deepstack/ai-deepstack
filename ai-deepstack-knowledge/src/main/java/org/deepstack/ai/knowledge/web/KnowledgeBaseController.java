package org.deepstack.ai.knowledge.web;

import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBaseCreateRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBasePageRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBaseUpdateRequest;
import org.deepstack.ai.knowledge.model.dto.response.KnowledgeBaseResponse;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import org.deepstack.ai.kernel.enums.common.EnabledStatusEnum;
import org.deepstack.ai.kernel.model.PageInfo;
import org.deepstack.ai.kernel.model.Response;
import org.deepstack.ai.kernel.model.SelectOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理 API（客户端）
 * <p>
 * 单体统一 API。
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 分页查询知识库列表。
     *
     * @param req 分页与筛选条件
     * @return 知识库分页结果
     */
    @GetMapping("/page")
    public Response<PageInfo<KnowledgeBaseResponse>> page(KnowledgeBasePageRequest req) {
        log.info("分页查询知识库");
        return Response.success(PageInfoUtils.of(knowledgeBaseService.page(req), this::toResponse));
    }

    /**
     * 启用知识库下拉选项（value=baseCode / label=名称）。
     */
    @GetMapping("/options")
    public Response<List<SelectOption>> options() {
        log.info("知识库下拉选项");
        List<SelectOption> options = knowledgeBaseService.listEnabled().stream()
                .map(kb -> SelectOption.of(
                        kb.getBaseCode(),
                        kb.getBaseName() + " (" + kb.getBaseCode() + ")"))
                .toList();
        return Response.success(options);
    }

    /**
     * 按主键查询知识库详情。
     *
     * @param id 知识库 id
     * @return 知识库详情，不存在时为 null
     */
    @GetMapping("/{id}")
    public Response<KnowledgeBaseResponse> get(@PathVariable("id") Long id) {        log.info("查询知识库详情: id={}", id);
        KnowledgeBase entity = knowledgeBaseService.getById(id);
        return Response.success(entity != null ? toResponse(entity) : null);
    }

    /**
     * 创建知识库。
     *
     * @param req 创建请求（baseCode、名称、embedding 模型等）
     */
    @PostMapping
    public Response<Void> create(@RequestBody KnowledgeBaseCreateRequest req) {
        log.info("创建知识库: baseCode={}", req != null ? req.getBaseCode() : null);
        knowledgeBaseService.create(req);
        return Response.success();
    }

    /**
     * 更新知识库配置。
     *
     * @param req 更新请求（含 id）
     */
    @PutMapping
    public Response<Void> update(@RequestBody KnowledgeBaseUpdateRequest req) {
        log.info("更新知识库: id={}", req != null ? req.getId() : null);
        knowledgeBaseService.update(req);
        return Response.success();
    }

    /**
     * 启用知识库。
     *
     * @param id 知识库 id
     */
    @PutMapping("/{id}/enable")
    public Response<Void> enable(@PathVariable("id") Long id) {
        log.info("启用知识库: id={}", id);
        knowledgeBaseService.enable(id);
        return Response.success();
    }

    /**
     * 禁用知识库。
     *
     * @param id 知识库 id
     */
    @PutMapping("/{id}/disable")
    public Response<Void> disable(@PathVariable("id") Long id) {
        log.info("禁用知识库: id={}", id);
        knowledgeBaseService.disable(id);
        return Response.success();
    }

    /**
     * 删除知识库。
     *
     * @param id 知识库 id
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("删除知识库: id={}", id);
        knowledgeBaseService.deleteById(id);
        return Response.success();
    }

    /**
     * Entity → Response 映射。
     * <p>
     * embeddingModelName / documentCount / chunkCount 为统计字段，
     * 需 join 查询，当前版本暂不填充（前端可通过 modelCode 联查）。
     * </p>
     */
    private KnowledgeBaseResponse toResponse(KnowledgeBase e) {
        KnowledgeBaseResponse r = new KnowledgeBaseResponse();
        r.setId(e.getId());
        r.setBaseCode(e.getBaseCode());
        r.setBaseName(e.getBaseName());
        r.setDescription(e.getDescription());
        r.setDomain(e.getDomain());
        r.setEmbeddingModelCode(e.getEmbeddingModelCode());
        r.setChunkSize(e.getChunkSize());
        r.setChunkOverlap(e.getChunkOverlap());
        r.setTopK(e.getTopK());
        r.setSimilarityThreshold(e.getSimilarityThreshold());
        r.setEnableGraph(e.getEnableGraph());
        r.setGraphModelCode(e.getGraphModelCode());
        r.setEnabled(e.getEnabled());
        r.setEnabledName(EnabledStatusEnum.labelOf(e.getEnabled()));
        r.setCreateTime(e.getCreateTime());
        r.setUpdateTime(e.getUpdateTime());
        return r;
    }
}
