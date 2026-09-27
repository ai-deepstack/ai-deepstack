package org.deepstack.ai.aimodel.service.impl;

import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.kernel.enums.model.ModelVisibilityEnum;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.AppUserInfo;
import org.deepstack.ai.aimodel.event.AiModelChangedEvent;
import org.deepstack.ai.aimodel.model.dto.request.AiModelCreateRequest;
import org.deepstack.ai.aimodel.model.dto.request.AiModelPageRequest;
import org.deepstack.ai.aimodel.model.dto.request.AiModelUpdateRequest;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.aimodel.mapper.AiModelMapper;
import org.deepstack.ai.aimodel.service.AiModelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 模型配置服务实现（CHAT + EMBEDDING）
 *
 * <p>配置变更后发布 {@link AiModelChangedEvent}，由 infra 侧清理 Chat/Embedding 客户端缓存。</p>
 * <p>可见性：管理员看全部；其他人仅公共 + 自己的私有。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl extends ServiceImpl<AiModelMapper, AiModel> implements AiModelService {

    private final ApplicationEventPublisher eventPublisher;

    // ===== 运行时查询 =====

    /**
     * 按模型编码查询已启用的模型。
     *
     * @param modelCode 模型编码
     * @return 启用中的模型；不存在或未启用返回 null
     */
    @Override
    public AiModel getByModelCode(String modelCode) {
        log.info("getByModelCode: modelCode={}", modelCode);
        AiModel model = baseMapper.selectOne(
                new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getModelCode, modelCode)
                        .eq(AiModel::getEnabled, YesNo.YES.getCode())
        );
        if (model == null) {
            log.warn("启用模型未找到: modelCode={}", modelCode);
        }
        return model;
    }

    /**
     * 列出全部已启用模型。
     *
     * @return 启用模型列表（按 id 升序）
     */
    @Override
    public List<AiModel> listEnabled() {
        log.info("listEnabled: 查询全部启用模型");
        return baseMapper.selectList(
                new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getEnabled, YesNo.YES.getCode())
                        .orderByAsc(AiModel::getId)
        );
    }

    /**
     * 按模型类型列出已启用模型。
     *
     * @param modelType 模型类型（如 CHAT / EMBEDDING）
     * @return 启用模型列表
     */
    @Override
    public List<AiModel> listEnabledByType(Integer modelType) {
        log.info("listEnabledByType: modelType={}", modelType);
        return baseMapper.selectList(
                new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getEnabled, YesNo.YES.getCode())
                        .eq(AiModel::getModelType, modelType)
                        .orderByAsc(AiModel::getId)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AiModel> listVisibleEnabled(Integer modelType, AppUserInfo user) {
        log.info("listVisibleEnabled: modelType={}, userId={}, isAdmin={}",
                modelType, user != null ? user.getUserId() : null, user != null ? user.getIsAdmin() : null);
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getEnabled, YesNo.YES.getCode())
                .eq(modelType != null, AiModel::getModelType, modelType)
                .orderByAsc(AiModel::getId);
        applyVisibilityFilter(wrapper, user);
        return baseMapper.selectList(wrapper);
    }

    /** 发布模型变更事件以清理客户端缓存。 */
    private void publishChanged(String modelCode) {
        log.info("publish AiModelChangedEvent: modelCode={}", modelCode);
        eventPublisher.publishEvent(new AiModelChangedEvent(modelCode));
    }

    // ===== CRUD =====

    /**
     * 分页查询模型（按可见性过滤）。
     *
     * @param req  分页与筛选条件
     * @param user 当前登录用户
     * @return 分页结果
     */
    @Override
    public IPage<AiModel> page(AiModelPageRequest req, AppUserInfo user) {
        log.info("page: pageNum={}, pageSize={}, modelType={}, provider={}, enabled={}, keyword={}, userId={}",
                req.getPageNum(), req.getPageSize(), req.getModelType(), req.getProvider(),
                req.getEnabled(), req.getKeyword(), user != null ? user.getUserId() : null);
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<AiModel>()
                .eq(req.getModelType() != null, AiModel::getModelType, req.getModelType())
                .eq(req.getProvider() != null, AiModel::getProvider, req.getProvider())
                .eq(req.getEnabled() != null, AiModel::getEnabled, req.getEnabled())
                .orderByDesc(AiModel::getUpdateTime);
        applyVisibilityFilter(wrapper, user);

        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(AiModel::getModelCode, req.getKeyword())
                    .or()
                    .like(AiModel::getModelName, req.getKeyword()));
        }

        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * 创建模型配置（不在日志中输出 apiKey）。
     *
     * @param req  创建请求
     * @param user 当前登录用户
     * @return 新模型 ID
     */
    @Override
    public Long create(AiModelCreateRequest req, AppUserInfo user) {
        log.info("create: modelCode={}, modelType={}, provider={}, visibility={}, userId={}",
                req.getModelCode(), req.getModelType(), req.getProvider(), req.getVisibility(),
                user != null ? user.getUserId() : null);
        int visibility = req.getVisibility() != null
                ? req.getVisibility()
                : ModelVisibilityEnum.PUBLIC.getCode();
        ModelVisibilityEnum vis = ModelVisibilityEnum.ofRequired(visibility);
        if (vis == ModelVisibilityEnum.PUBLIC && !isAdmin(user)) {
            log.warn("create public model denied: userId={}", user != null ? user.getUserId() : null);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED.getCode(), "仅管理员可创建公共模型");
        }
        AiModel entity = new AiModel();
        entity.setModelCode(req.getModelCode());
        entity.setModelName(req.getModelName());
        entity.setModelType(req.getModelType());
        entity.setProvider(req.getProvider());
        entity.setBaseUrl(req.getBaseUrl());
        entity.setApiKey(req.getApiKey());
        entity.setApiModelName(req.getApiModelName());
        entity.setExtraJson(req.getExtraJson());
        entity.setEnabled(req.getEnabled());
        entity.setRemark(req.getRemark());
        entity.setVisibility(vis.getCode());
        if (vis == ModelVisibilityEnum.PRIVATE) {
            if (user == null || user.getUserId() == null) {
                throw new BusinessException(CommonErrorCode.NEED_LOGIN);
            }
            entity.setOwnerId(user.getUserId());
        } else {
            entity.setOwnerId(user != null ? user.getUserId() : null);
        }
        baseMapper.insert(entity);
        log.info("Created ai model: id={}, modelCode={}, visibility={}, ownerId={}",
                entity.getId(), entity.getModelCode(), entity.getVisibility(), entity.getOwnerId());
        return entity.getId();
    }

    /**
     * 更新模型配置（不含 apiKey）。
     *
     * @param req  更新请求
     * @param user 当前登录用户
     */
    @Override
    public void update(AiModelUpdateRequest req, AppUserInfo user) {
        log.info("update: id={}, userId={}", req.getId(), user != null ? user.getUserId() : null);
        AiModel entity = requireVisibleWritable(req.getId(), user);
        if (req.getModelCode() != null) entity.setModelCode(req.getModelCode());
        if (req.getModelName() != null) entity.setModelName(req.getModelName());
        if (req.getModelType() != null) entity.setModelType(req.getModelType());
        if (req.getProvider() != null) entity.setProvider(req.getProvider());
        if (req.getBaseUrl() != null) entity.setBaseUrl(req.getBaseUrl());
        if (req.getApiModelName() != null) entity.setApiModelName(req.getApiModelName());
        if (req.getExtraJson() != null) entity.setExtraJson(req.getExtraJson());
        if (req.getEnabled() != null) entity.setEnabled(req.getEnabled());
        if (req.getRemark() != null) entity.setRemark(req.getRemark());
        if (req.getVisibility() != null) {
            ModelVisibilityEnum vis = ModelVisibilityEnum.ofRequired(req.getVisibility());
            if (vis == ModelVisibilityEnum.PUBLIC && !isAdmin(user)) {
                log.warn("update visibility public denied: id={}, userId={}",
                        req.getId(), user != null ? user.getUserId() : null);
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED.getCode(), "仅管理员可将模型设为公共");
            }
            entity.setVisibility(vis.getCode());
            if (vis == ModelVisibilityEnum.PRIVATE && entity.getOwnerId() == null && user != null) {
                entity.setOwnerId(user.getUserId());
            }
        }
        baseMapper.updateById(entity);
        publishChanged(entity.getModelCode());
        log.info("Updated ai model: id={}", req.getId());
    }

    /**
     * 单独更新模型 API Key。
     *
     * @param id     模型 ID
     * @param apiKey 新 API Key
     * @param user   当前登录用户
     */
    @Override
    public void updateApiKey(Long id, String apiKey, AppUserInfo user) {
        log.info("updateApiKey: id={}, userId={}", id, user != null ? user.getUserId() : null);
        AiModel entity = requireVisibleWritable(id, user);
        entity.setApiKey(apiKey);
        baseMapper.updateById(entity);
        publishChanged(entity.getModelCode());
        log.info("Updated apiKey for ai model: id={}", id);
    }

    /**
     * 启用模型（须有写权限）。
     *
     * @param id   模型 ID
     * @param user 当前登录用户
     */
    @Override
    public void enable(Long id, AppUserInfo user) {
        log.info("enable: id={}, userId={}", id, user != null ? user.getUserId() : null);
        AiModel entity = requireVisibleWritable(id, user);
        entity.setEnabled(YesNo.YES.getCode());
        baseMapper.updateById(entity);
        publishChanged(entity.getModelCode());
        log.info("Enabled ai model: id={}", id);
    }

    /**
     * 禁用模型（须有写权限）。
     *
     * @param id   模型 ID
     * @param user 当前登录用户
     */
    @Override
    public void disable(Long id, AppUserInfo user) {
        log.info("disable: id={}, userId={}", id, user != null ? user.getUserId() : null);
        AiModel entity = requireVisibleWritable(id, user);
        entity.setEnabled(YesNo.NO.getCode());
        baseMapper.updateById(entity);
        publishChanged(entity.getModelCode());
        log.info("Disabled ai model: id={}", id);
    }

    /**
     * 删除模型（须有写权限）。
     *
     * @param id   模型 ID
     * @param user 当前登录用户
     */
    @Override
    public void delete(Long id, AppUserInfo user) {
        log.info("delete: id={}, userId={}", id, user != null ? user.getUserId() : null);
        AiModel entity = requireVisibleWritable(id, user);
        String modelCode = entity.getModelCode();
        baseMapper.deleteById(id);
        publishChanged(modelCode);
        log.info("Deleted ai model: id={}, modelCode={}", id, modelCode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canSeeApiKey(AiModel model, AppUserInfo user) {
        if (model == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        if (ModelVisibilityEnum.PRIVATE.matches(model.getVisibility())) {
            return isOwner(user, model);
        }
        // 公共：列表已可见的用户可看脱敏 key
        return true;
    }

    /**
     * 应用可见性过滤：管理员不过滤；其他人看公共 + 自己的私有。
     *
     * @param wrapper 查询包装
     * @param user    当前用户
     */
    private void applyVisibilityFilter(LambdaQueryWrapper<AiModel> wrapper, AppUserInfo user) {
        if (isAdmin(user)) {
            return;
        }
        Long uid = user != null ? user.getUserId() : null;
        wrapper.and(w -> w.eq(AiModel::getVisibility, ModelVisibilityEnum.PUBLIC.getCode())
                .or(uid != null, x -> x.eq(AiModel::getVisibility, ModelVisibilityEnum.PRIVATE.getCode())
                        .eq(AiModel::getOwnerId, uid))
                .or(x -> x.isNull(AiModel::getVisibility)));
    }

    /**
     * 加载模型并校验写权限：私有需所有者或管理员；公共仅管理员。
     *
     * @param id   模型 id
     * @param user 当前用户
     * @return 实体
     */
    private AiModel requireVisibleWritable(Long id, AppUserInfo user) {
        AiModel entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("模型不存在: id=" + id);
        }
        if (isAdmin(user)) {
            return entity;
        }
        if (ModelVisibilityEnum.PRIVATE.matches(entity.getVisibility())) {
            if (isOwner(user, entity)) {
                return entity;
            }
            log.warn("write private model denied: id={}, userId={}", id, user != null ? user.getUserId() : null);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED.getCode(), "无权修改该私有模型");
        }
        log.warn("write public model denied: id={}, userId={}", id, user != null ? user.getUserId() : null);
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED.getCode(), "仅管理员可修改公共模型");
    }

    /**
     * 是否私有模型所有者。
     *
     * @param user  当前用户
     * @param model 模型
     * @return true=所有者
     */
    private static boolean isOwner(AppUserInfo user, AiModel model) {
        return user != null && user.getUserId() != null
                && user.getUserId().equals(model.getOwnerId());
    }

    /**
     * 是否管理员。
     *
     * @param user 当前用户
     * @return true=管理员
     */
    private static boolean isAdmin(AppUserInfo user) {
        return user != null && YesNo.isYes(user.getIsAdmin());
    }
}
