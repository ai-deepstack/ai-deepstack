package org.deepstack.ai.agent.web;

import org.deepstack.ai.agent.model.dto.request.AiIntentCreateRequest;
import org.deepstack.ai.agent.model.dto.request.AiIntentPageRequest;
import org.deepstack.ai.agent.model.dto.request.AiIntentUpdateRequest;
import org.deepstack.ai.agent.model.dto.response.AiIntentResponse;
import org.deepstack.ai.agent.model.entity.AiIntent;
import org.deepstack.ai.agent.service.AiIntentService;
import org.deepstack.ai.kernel.common.PageInfoUtils;
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
 * 租户级意图字典管理 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/intents")
@RequiredArgsConstructor
public class IntentController {

    private final AiIntentService aiIntentService;

    /**
     * 分页查询意图。
     *
     * @param req 分页与筛选
     * @return 分页响应
     */
    @GetMapping("/page")
    public Response<PageInfo<AiIntentResponse>> page(AiIntentPageRequest req) {
        log.info("API intents page: pageNum={}, pageSize={}, keyword={}",
                req != null ? req.getPageNum() : null,
                req != null ? req.getPageSize() : null,
                req != null ? req.getKeyword() : null);
        return Response.success(PageInfoUtils.of(aiIntentService.page(req), this::toResponse));
    }

    /**
     * 启用意图下拉选项（value=intentCode / label=名称 (code)）。
     */
    @GetMapping("/options")
    public Response<List<SelectOption>> options() {
        log.info("API intents options");
        List<SelectOption> options = aiIntentService.listEnabledByTenant(AiIntentService.DEFAULT_TENANT_ID).stream()
                .map(i -> SelectOption.of(
                        i.getIntentCode(),
                        i.getIntentName() + " (" + i.getIntentCode() + ")"))
                .toList();
        return Response.success(options);
    }

    /**
     * 按主键查询意图详情。
     *
     * @param id 意图主键
     * @return 详情；不存在时 data 为 null
     */
    @GetMapping("/{id}")
    public Response<AiIntentResponse> get(@PathVariable("id") Long id) {
        log.info("API get intent: id={}", id);
        return Response.success(aiIntentService.getDetail(id));
    }

    /**
     * 创建意图。
     *
     * @param req 创建请求
     */
    @PostMapping
    public Response<Void> create(@RequestBody AiIntentCreateRequest req) {
        log.info("API create intent: code={}", req != null ? req.getIntentCode() : null);
        aiIntentService.create(req);
        return Response.success();
    }

    /**
     * 更新意图。
     *
     * @param req 更新请求
     */
    @PutMapping
    public Response<Void> update(@RequestBody AiIntentUpdateRequest req) {
        log.info("API update intent: id={}", req != null ? req.getId() : null);
        aiIntentService.update(req);
        return Response.success();
    }

    /**
     * 启用意图。
     *
     * @param id 意图主键
     */
    @PutMapping("/{id}/enable")
    public Response<Void> enable(@PathVariable("id") Long id) {
        log.info("API enable intent: id={}", id);
        aiIntentService.enable(id);
        return Response.success();
    }

    /**
     * 停用意图。
     *
     * @param id 意图主键
     */
    @PutMapping("/{id}/disable")
    public Response<Void> disable(@PathVariable("id") Long id) {
        log.info("API disable intent: id={}", id);
        aiIntentService.disable(id);
        return Response.success();
    }

    /**
     * 删除意图（逻辑删除）。
     *
     * @param id 意图主键
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id) {
        log.info("API delete intent: id={}", id);
        aiIntentService.removeById(id);
        return Response.success();
    }

    /**
     * 实体转响应 DTO。
     *
     * @param e 意图实体
     * @return 响应
     */
    private AiIntentResponse toResponse(AiIntent e) {
        AiIntentResponse r = new AiIntentResponse();
        r.setId(e.getId());
        r.setTenantId(e.getTenantId());
        r.setIntentCode(e.getIntentCode());
        r.setIntentName(e.getIntentName());
        r.setDescription(e.getDescription());
        r.setSortOrder(e.getSortOrder());
        r.setEnabled(e.getEnabled());
        r.setEnabledName(EnabledStatusEnum.labelOf(e.getEnabled()));
        r.setCreateTime(e.getCreateTime());
        r.setUpdateTime(e.getUpdateTime());
        return r;
    }
}
