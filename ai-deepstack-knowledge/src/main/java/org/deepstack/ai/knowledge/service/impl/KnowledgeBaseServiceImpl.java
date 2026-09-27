package org.deepstack.ai.knowledge.service.impl;


import org.deepstack.ai.graph.GraphStore;
import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.knowledge.graph.KnowledgeGraphScopes;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBaseCreateRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBasePageRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBaseUpdateRequest;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import org.deepstack.ai.knowledge.mapper.KnowledgeBaseMapper;
import org.deepstack.ai.aimodel.service.AiModelService;
import org.deepstack.ai.knowledge.service.KnowledgeBaseService;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 知识库服务实现
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    private final AiModelService aiModelService;
    private final ObjectProvider<GraphStore> graphStoreProvider;

    // ===== 运行时查询 =====

    /**
     * 按主键查询已启用知识库。
     *
     * @param id 知识库 ID
     * @return 启用中的知识库；不存在或未启用返回 null
     */
    @Override
    public KnowledgeBase getEnabledById(Long id) {
        log.info("getEnabledById: id={}", id);
        KnowledgeBase kb = baseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getId, id)
                        .eq(KnowledgeBase::getEnabled, YesNo.YES.getCode())
        );
        if (kb == null) {
            log.warn("启用知识库未找到: id={}", id);
        }
        return kb;
    }

    /**
     * 按编码查询已启用知识库。
     *
     * @param baseCode 知识库编码
     * @return 启用中的知识库；不存在或未启用返回 null
     */
    @Override
    public KnowledgeBase getByBaseCode(String baseCode) {
        log.info("getByBaseCode: baseCode={}", baseCode);
        KnowledgeBase kb = baseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getBaseCode, baseCode)
                        .eq(KnowledgeBase::getEnabled, YesNo.YES.getCode())
        );
        if (kb == null) {
            log.warn("启用知识库未找到: baseCode={}", baseCode);
        }
        return kb;
    }

    /**
     * 按编码批量查询已启用知识库（IN 一次）。
     */
    @Override
    public List<KnowledgeBase> listByBaseCodes(List<String> baseCodes) {
        if (baseCodes == null || baseCodes.isEmpty()) {
            return List.of();
        }
        List<String> codes = baseCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return List.of();
        }
        log.info("listByBaseCodes: count={}", codes.size());
        List<KnowledgeBase> list = baseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .in(KnowledgeBase::getBaseCode, codes)
                        .eq(KnowledgeBase::getEnabled, YesNo.YES.getCode())
        );
        log.info("listByBaseCodes 完成: requested={}, found={}", codes.size(), list.size());
        return list;
    }

    /**
     * 列出全部已启用知识库。
     *
     * @return 启用知识库列表
     */
    @Override
    public List<KnowledgeBase> listEnabled() {
        log.info("listEnabled: 查询全部启用知识库");
        return baseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getEnabled, YesNo.YES.getCode())
                        .orderByAsc(KnowledgeBase::getId)
        );
    }

    // ===== CRUD =====

    /**
     * 分页查询知识库。
     *
     * @param req 分页与筛选条件
     * @return 分页结果
     */
    @Override
    public IPage<KnowledgeBase> page(KnowledgeBasePageRequest req) {
        log.info("page: pageNum={}, pageSize={}, domain={}, enabled={}, keyword={}",
                req.getPageNum(), req.getPageSize(), req.getDomain(), req.getEnabled(), req.getKeyword());
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(StringUtils.hasText(req.getDomain()), KnowledgeBase::getDomain, req.getDomain())
                .eq(req.getEnabled() != null, KnowledgeBase::getEnabled, req.getEnabled())
                .orderByDesc(KnowledgeBase::getUpdateTime);

        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(KnowledgeBase::getBaseCode, req.getKeyword())
                    .or()
                    .like(KnowledgeBase::getBaseName, req.getKeyword()));
        }

        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * 创建知识库（校验 embedding 模型与 baseCode 唯一性）。
     *
     * @param req 创建请求
     * @return 新知识库 ID
     */
    @Override
    public Long create(KnowledgeBaseCreateRequest req) {
        log.info("create: baseCode={}, embeddingModelCode={}, domain={}",
                req.getBaseCode(), req.getEmbeddingModelCode(), req.getDomain());
        validateEmbeddingModel(req.getEmbeddingModelCode());

        // baseCode 唯一性校验
        Long count = baseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getBaseCode, req.getBaseCode())
        );
        if (count != null && count > 0) {
            log.warn("create 知识库编码已存在: baseCode={}", req.getBaseCode());
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "知识库编码已存在: " + req.getBaseCode());
        }

        KnowledgeBase entity = new KnowledgeBase();
        entity.setBaseCode(req.getBaseCode());
        entity.setBaseName(req.getBaseName());
        entity.setDescription(req.getDescription());
        entity.setDomain(req.getDomain());
        entity.setEmbeddingModelCode(req.getEmbeddingModelCode());
        entity.setChunkSize(req.getChunkSize() == null ? 800 : req.getChunkSize());
        entity.setChunkOverlap(req.getChunkOverlap() == null ? 200 : req.getChunkOverlap());
        entity.setTopK(req.getTopK() == null ? 5 : req.getTopK());
        entity.setSimilarityThreshold(
                req.getSimilarityThreshold() == null ? new BigDecimal("0.700") : req.getSimilarityThreshold());
        entity.setEnableGraph(req.getEnableGraph() == null ? YesNo.NO.getCode() : req.getEnableGraph());
        entity.setGraphModelCode(req.getGraphModelCode());
        entity.setEnabled(req.getEnabled() == null ? YesNo.YES.getCode() : req.getEnabled());

        baseMapper.insert(entity);

        log.info("Created knowledge base: id={}, baseCode={}, embeddingModelCode={}",
                entity.getId(), entity.getBaseCode(), entity.getEmbeddingModelCode());
        return entity.getId();
    }

    /**
     * 更新知识库配置。
     *
     * @param req 更新请求
     */
    @Override
    public void update(KnowledgeBaseUpdateRequest req) {
        log.info("update: id={}", req.getId());
        KnowledgeBase entity = baseMapper.selectById(req.getId());
        if (entity == null) {
            log.warn("update 知识库不存在: id={}", req.getId());
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: id=" + req.getId());
        }
        // 切换 embedding 模型时校验类型
        if (req.getEmbeddingModelCode() != null
                && !req.getEmbeddingModelCode().equals(entity.getEmbeddingModelCode())) {
            validateEmbeddingModel(req.getEmbeddingModelCode());
            entity.setEmbeddingModelCode(req.getEmbeddingModelCode());
        }
        if (req.getBaseName() != null) entity.setBaseName(req.getBaseName());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());
        if (req.getDomain() != null) entity.setDomain(req.getDomain());
        if (req.getChunkSize() != null) entity.setChunkSize(req.getChunkSize());
        if (req.getChunkOverlap() != null) entity.setChunkOverlap(req.getChunkOverlap());
        if (req.getTopK() != null) entity.setTopK(req.getTopK());
        if (req.getSimilarityThreshold() != null) entity.setSimilarityThreshold(req.getSimilarityThreshold());
        if (req.getEnableGraph() != null) entity.setEnableGraph(req.getEnableGraph());
        if (req.getGraphModelCode() != null) entity.setGraphModelCode(req.getGraphModelCode());
        if (req.getEnabled() != null) entity.setEnabled(req.getEnabled());

        baseMapper.updateById(entity);
        log.info("Updated knowledge base: id={}", req.getId());
    }

    /**
     * 启用知识库。
     *
     * @param id 知识库 ID
     */
    @Override
    public void enable(Long id) {
        log.info("enable: id={}", id);
        toggleEnabled(id, true);
    }

    /**
     * 禁用知识库。
     *
     * @param id 知识库 ID
     */
    @Override
    public void disable(Long id) {
        log.info("disable: id={}", id);
        toggleEnabled(id, false);
    }

    /**
     * 删除知识库，并尽力清理 AGE scope（失败只打日志，不阻断删库）。
     *
     * @param id 知识库 ID
     */
    @Override
    public void deleteById(Long id) {
        log.info("deleteById: id={}", id);
        KnowledgeBase kb = baseMapper.selectById(id);
        baseMapper.deleteById(id);
        if (kb != null && StringUtils.hasText(kb.getBaseCode())) {
            clearGraphScope(kb.getBaseCode());
        }
        log.info("Deleted knowledge base: id={}, baseCode={}",
                id, kb != null ? kb.getBaseCode() : null);
    }

    /**
     * 删除知识库对应图隔离域 {@code kb:{baseCode}}。
     */
    private void clearGraphScope(String baseCode) {
        GraphStore store = graphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("clearGraphScope 跳过: GraphStore 未装配, baseCode={}", baseCode);
            return;
        }
        if (!store.info().isReady()) {
            log.warn("clearGraphScope 跳过: GraphStore 未就绪, baseCode={}, detail={}",
                    baseCode, store.info().getDetail());
            return;
        }
        String scope = KnowledgeGraphScopes.scopeOf(baseCode);
        try {
            log.info("clearGraphScope: baseCode={}, scope={}", baseCode, scope);
            store.deleteScope(scope);
            log.info("clearGraphScope 完成: scope={}", scope);
        } catch (Exception e) {
            log.warn("clearGraphScope 失败（库已删，需人工核对图残留）: scope={}, err={}",
                    scope, e.getMessage());
        }
    }

    // ===== private =====

    /**
     * 校验 embedding 模型存在、类型为 EMBEDDING、且启用。
     *
     * @param embeddingModelCode 模型编码
     */
    private void validateEmbeddingModel(String embeddingModelCode) {
        log.debug("validateEmbeddingModel: code={}", embeddingModelCode);
        if (!StringUtils.hasText(embeddingModelCode)) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(), "embedding 模型编码不能为空");
        }
        AiModel model = aiModelService.getByModelCode(embeddingModelCode);
        if (model == null) {
            log.warn("embedding 模型不存在或已禁用: code={}", embeddingModelCode);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "embedding 模型不存在或已禁用: code=" + embeddingModelCode);
        }
        if (model.getModelType() == null
                || model.getModelType() != org.deepstack.ai.kernel.enums.model.ModelTypeEnum.EMBEDDING.getCode()) {
            log.warn("模型类型不是 EMBEDDING: code={}, modelType={}", embeddingModelCode, model.getModelType());
            throw new BusinessException(CommonErrorCode.INVALID_PARAM.getCode(),
                    "模型类型不是 EMBEDDING: code=" + embeddingModelCode + ", modelType=" + model.getModelType());
        }
    }

    /**
     * 切换知识库启用状态。
     *
     * @param id      知识库 ID
     * @param enabled true=启用，false=禁用
     */
    private void toggleEnabled(Long id, boolean enabled) {
        KnowledgeBase entity = baseMapper.selectById(id);
        if (entity == null) {
            log.warn("toggleEnabled 知识库不存在: id={}", id);
            throw new BusinessException(CommonErrorCode.NOT_FOUND.getCode(),
                    "知识库不存在: id=" + id);
        }
        entity.setEnabled(YesNo.codeOf(enabled));
        baseMapper.updateById(entity);
        log.info("{} knowledge base: id={}", enabled ? "Enabled" : "Disabled", id);
    }
}
