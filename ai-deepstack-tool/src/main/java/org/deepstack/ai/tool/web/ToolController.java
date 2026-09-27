package org.deepstack.ai.tool.web;

import org.deepstack.ai.tool.model.dto.request.AiToolCreateRequest;
import org.deepstack.ai.tool.model.dto.request.AiToolPageRequest;
import org.deepstack.ai.tool.model.dto.request.AiToolUpdateRequest;
import org.deepstack.ai.tool.model.dto.response.AiToolResponse;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.tool.index.ToolIndexHydrator;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.tool.model.entity.AiTool;
import org.deepstack.ai.tool.service.AiToolService;
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
 * AI 工具目录管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final AiToolService aiToolService;
    private final ToolPort toolPort;
    private final ToolIndexHydrator toolIndexHydrator;

    /**
     * 分页查询工具目录。
     *
     * @param req 分页与筛选条件
     * @return 工具分页列表
     */
    @GetMapping("/page")
    public Response<PageInfo<AiToolResponse>> page(AiToolPageRequest req) {
        log.info("API 分页查询工具: pageNum={}, pageSize={}, enabled={}, keyword={}",
                req != null ? req.getPageNum() : null,
                req != null ? req.getPageSize() : null,
                req != null ? req.getEnabled() : null,
                req != null ? req.getKeyword() : null);
        return Response.success(PageInfoUtils.of(aiToolService.page(req), this::toResponse));
    }

    /**
     * 启用工具下拉/多选选项（value=工具 id，label=名称 (code)；绑定接口仍按 id）。
     *
     * @return 下拉选项列表
     */
    @GetMapping("/options")
    public Response<List<SelectOption>> options() {
        log.info("API 查询启用工具选项");
        List<SelectOption> options = aiToolService.listEnabled().stream()
                .map(t -> SelectOption.of(
                        String.valueOf(t.getId()),
                        t.getToolName() + " (" + t.getToolCode() + ")"))
                .toList();
        return Response.success(options);
    }

    /**
     * 按主键查询工具详情。
     *
     * @param id 工具主键
     * @return 工具详情
     */
    @GetMapping("/{id}")
    public Response<AiToolResponse> get(@PathVariable("id") Long id) {
        log.info("API 查询工具详情: id={}", id);
        return Response.success(aiToolService.getDetail(id));
    }

    /**
     * 新建 LOCAL 工具配置。
     *
     * @param req 创建请求
     * @return 空成功响应
     */
    @PostMapping
    public Response<Void> create(@RequestBody AiToolCreateRequest req) {
        log.info("API 创建工具: toolCode={}, handlerBean={}",
                req != null ? req.getToolCode() : null,
                req != null ? req.getHandlerBean() : null);
        aiToolService.create(req);
        toolPort.evictAll();
        toolIndexHydrator.refreshAll();
        return Response.success();
    }

    /**
     * 更新工具配置。
     *
     * @param req 更新请求
     * @return 空成功响应
     */
    @PutMapping
    public Response<Void> update(@RequestBody AiToolUpdateRequest req) {
        log.info("API 更新工具: id={}", req != null ? req.getId() : null);
        aiToolService.update(req);
        toolPort.evictAll();
        toolIndexHydrator.refreshAll();
        return Response.success();
    }

    /**
     * 启用工具。
     *
     * @param id 工具主键
     * @return 空成功响应
     */
    @PutMapping("/{id}/enable")
    public Response<Void> enable(@PathVariable("id") Long id) {
        log.info("API 启用工具: id={}", id);
        aiToolService.enable(id);
        toolPort.evictAll();
        toolIndexHydrator.refreshAll();
        return Response.success();
    }

    /**
     * 禁用工具。
     *
     * @param id 工具主键
     * @return 空成功响应
     */
    @PutMapping("/{id}/disable")
    public Response<Void> disable(@PathVariable("id") Long id) {
        log.info("API 禁用工具: id={}", id);
        aiToolService.disable(id);
        toolPort.evictAll();
        toolIndexHydrator.refreshAll();
        return Response.success();
    }

    /**
     * 删除工具。
     *
     * @param id 工具主键
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("API 删除工具: id={}", id);
        aiToolService.removeById(id);
        toolPort.evictAll();
        toolIndexHydrator.refreshAll();
        return Response.success();
    }

    /** 分页结果实体转响应 DTO。 */
    private AiToolResponse toResponse(AiTool t) {
        return aiToolService.getDetail(t.getId());
    }
}
