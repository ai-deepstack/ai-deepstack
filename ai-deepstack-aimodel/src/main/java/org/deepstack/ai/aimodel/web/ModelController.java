package org.deepstack.ai.aimodel.web;

import org.deepstack.ai.aimodel.model.dto.request.AiModelCreateRequest;
import org.deepstack.ai.aimodel.model.dto.request.AiModelPageRequest;
import org.deepstack.ai.aimodel.model.dto.request.AiModelUpdateRequest;
import org.deepstack.ai.aimodel.model.dto.request.ApiKeyUpdateRequest;
import org.deepstack.ai.aimodel.model.dto.response.AiModelResponse;
import org.deepstack.ai.kernel.auth.LoginUser;
import org.deepstack.ai.kernel.common.PageInfoUtils;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.kernel.enums.common.EnabledStatusEnum;
import org.deepstack.ai.kernel.model.AppUserInfo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 模型管理 API（客户端）
 * <p>
 * 单体统一 API。
 * 支持 CHAT 和 EMBEDDING 两种类型的模型管理。
 * </p>
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final AiModelService aiModelService;

    /**
     * 分页查询模型列表（按可见性过滤）。
     *
     * @param req       分页与筛选条件（类型、启用状态等）
     * @param loginUser 当前登录用户
     * @return 模型分页结果（API Key 已脱敏）
     */
    @GetMapping("/page")
    public Response<PageInfo<AiModelResponse>> page(AiModelPageRequest req,
                                                    @LoginUser AppUserInfo loginUser) {
        log.info("模型分页: pageNum={}, pageSize={}, modelType={}, enabled={}, userId={}",
                req != null ? req.getPageNum() : null,
                req != null ? req.getPageSize() : null,
                req != null ? req.getModelType() : null,
                req != null ? req.getEnabled() : null,
                loginUser != null ? loginUser.getUserId() : null);
        return Response.success(PageInfoUtils.of(aiModelService.page(req, loginUser),
                m -> toResponse(m, loginUser)));
    }

    /**
     * 启用模型下拉选项（仅 value=modelCode / label；按可见性过滤）。
     *
     * @param modelType 可选，按类型过滤（见 ModelTypeEnum）
     * @param loginUser 当前登录用户
     */
    @GetMapping("/options")
    public Response<List<SelectOption>> options(@RequestParam(required = false) Integer modelType,
                                                @LoginUser AppUserInfo loginUser) {
        log.info("模型下拉选项: modelType={}, userId={}",
                modelType, loginUser != null ? loginUser.getUserId() : null);
        List<AiModel> models = aiModelService.listVisibleEnabled(modelType, loginUser);
        List<SelectOption> options = models.stream()
                .map(m -> SelectOption.of(
                        m.getModelCode(),
                        m.getModelName() + " (" + m.getModelCode() + ")"))
                .toList();
        return Response.success(options);
    }

    /**
     * 按主键查询模型详情。
     *
     * @param id        模型 id
     * @param loginUser 当前登录用户
     * @return 模型详情（API Key 已脱敏），不存在时为 null
     */
    @GetMapping("/{id}")
    public Response<AiModelResponse> get(@PathVariable("id") Long id,
                                         @LoginUser AppUserInfo loginUser) {
        log.info("查询模型: id={}, userId={}", id, loginUser != null ? loginUser.getUserId() : null);
        AiModel model = aiModelService.getById(id);
        return Response.success(model != null ? toResponse(model, loginUser) : null);
    }

    /**
     * 创建模型配置。
     *
     * @param req       创建请求（编码、类型、provider、baseUrl 等）
     * @param loginUser 当前登录用户
     */
    @PostMapping
    public Response<Void> create(@RequestBody AiModelCreateRequest req,
                                 @LoginUser AppUserInfo loginUser) {
        log.info("创建模型: modelCode={}, modelType={}, userId={}",
                req != null ? req.getModelCode() : null,
                req != null ? req.getModelType() : null,
                loginUser != null ? loginUser.getUserId() : null);
        aiModelService.create(req, loginUser);
        return Response.success();
    }

    /**
     * 更新模型配置（不含 API Key）。
     *
     * @param req       更新请求（含 id）
     * @param loginUser 当前登录用户
     */
    @PutMapping
    public Response<Void> update(@RequestBody AiModelUpdateRequest req,
                                 @LoginUser AppUserInfo loginUser) {
        log.info("更新模型: id={}, userId={}",
                req != null ? req.getId() : null,
                loginUser != null ? loginUser.getUserId() : null);
        aiModelService.update(req, loginUser);
        return Response.success();
    }

    /**
     * 单独更新模型 API Key。
     *
     * @param id        模型 id
     * @param req       含新 apiKey
     * @param loginUser 当前登录用户
     */
    @PutMapping("/{id}/apiKey")
    public Response<Void> updateApiKey(@PathVariable("id") Long id,
                                       @RequestBody ApiKeyUpdateRequest req,
                                       @LoginUser AppUserInfo loginUser) {
        log.info("更新模型 API Key: id={}, userId={}",
                id, loginUser != null ? loginUser.getUserId() : null);
        aiModelService.updateApiKey(id, req.getApiKey(), loginUser);
        return Response.success();
    }

    /**
     * 启用模型。
     *
     * @param id        模型 id
     * @param loginUser 当前登录用户
     */
    @PutMapping("/{id}/enable")
    public Response<Void> enable(@PathVariable("id") Long id, @LoginUser AppUserInfo loginUser) {
        log.info("启用模型: id={}, userId={}", id, loginUser != null ? loginUser.getUserId() : null);
        aiModelService.enable(id, loginUser);
        return Response.success();
    }

    /**
     * 禁用模型。
     *
     * @param id        模型 id
     * @param loginUser 当前登录用户
     */
    @PutMapping("/{id}/disable")
    public Response<Void> disable(@PathVariable("id") Long id, @LoginUser AppUserInfo loginUser) {
        log.info("禁用模型: id={}, userId={}", id, loginUser != null ? loginUser.getUserId() : null);
        aiModelService.disable(id, loginUser);
        return Response.success();
    }

    /**
     * 删除模型。
     *
     * @param id        模型 id
     * @param loginUser 当前登录用户
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") Long id, @LoginUser AppUserInfo loginUser) {
        log.info("删除模型: id={}, userId={}", id, loginUser != null ? loginUser.getUserId() : null);
        aiModelService.delete(id, loginUser);
        return Response.success();
    }

    /**
     * Entity → API Response；仅在可见权限下返回脱敏 API Key。
     *
     * @param m         模型实体
     * @param loginUser 当前登录用户
     * @return 响应 DTO
     */
    private AiModelResponse toResponse(AiModel m, AppUserInfo loginUser) {
        AiModelResponse r = new AiModelResponse();
        r.setId(m.getId());
        r.setModelCode(m.getModelCode());
        r.setModelName(m.getModelName());
        r.setModelType(m.getModelType());
        r.setModelTypeName(org.deepstack.ai.kernel.enums.model.ModelTypeEnum.labelOf(m.getModelType()));
        r.setProvider(m.getProvider());
        r.setBaseUrl(m.getBaseUrl());
        r.setApiKey(aiModelService.canSeeApiKey(m, loginUser) ? maskApiKey(m.getApiKey()) : "");
        r.setApiModelName(m.getApiModelName());
        r.setExtraJson(m.getExtraJson());
        r.setEnabled(m.getEnabled());
        r.setEnabledName(EnabledStatusEnum.labelOf(m.getEnabled()));
        r.setVisibility(m.getVisibility());
        r.setVisibilityName(org.deepstack.ai.kernel.enums.model.ModelVisibilityEnum.labelOf(m.getVisibility()));
        r.setOwnerId(m.getOwnerId());
        r.setRemark(m.getRemark());
        r.setCreateTime(m.getCreateTime());
        r.setUpdateTime(m.getUpdateTime());
        return r;
    }

    /**
     * API Key 脱敏：保留前4位 + **** + 后3位；不足8位全部遮蔽
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 3);
    }
}
