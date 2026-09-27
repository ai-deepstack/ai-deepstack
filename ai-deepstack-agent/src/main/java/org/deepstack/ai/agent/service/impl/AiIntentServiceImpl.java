package org.deepstack.ai.agent.service.impl;

import org.deepstack.ai.agent.mapper.AiIntentMapper;
import org.deepstack.ai.agent.model.dto.request.AiIntentCreateRequest;
import org.deepstack.ai.agent.model.dto.request.AiIntentPageRequest;
import org.deepstack.ai.agent.model.dto.request.AiIntentUpdateRequest;
import org.deepstack.ai.agent.model.dto.response.AiIntentResponse;
import org.deepstack.ai.agent.model.entity.AiIntent;
import org.deepstack.ai.agent.service.AiIntentService;
import org.deepstack.ai.kernel.enums.common.YesNo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link AiIntentService} 实现：租户级意图字典 CRUD 与启停。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiIntentServiceImpl extends ServiceImpl<AiIntentMapper, AiIntent> implements AiIntentService {

    /**
     * {@inheritDoc}
     */
    @Override
    public IPage<AiIntent> page(AiIntentPageRequest req) {
        long tenantId = req.getTenantId() != null ? req.getTenantId() : DEFAULT_TENANT_ID;
        log.info("AiIntent page: tenantId={}, pageNum={}, pageSize={}, enabled={}, keyword={}",
                tenantId, req.getPageNum(), req.getPageSize(), req.getEnabled(), req.getKeyword());
        LambdaQueryWrapper<AiIntent> wrapper = new LambdaQueryWrapper<AiIntent>()
                .eq(AiIntent::getTenantId, tenantId)
                .eq(req.getEnabled() != null, AiIntent::getEnabled, req.getEnabled())
                .orderByAsc(AiIntent::getSortOrder)
                .orderByDesc(AiIntent::getUpdateTime);
        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.and(w -> w.like(AiIntent::getIntentCode, req.getKeyword())
                    .or()
                    .like(AiIntent::getIntentName, req.getKeyword()));
        }
        return baseMapper.selectPage(new Page<>(req.getPageNum(), req.getPageSize()), wrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AiIntentResponse getDetail(Long id) {
        AiIntent entity = baseMapper.selectById(id);
        return entity == null ? null : toResponse(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long create(AiIntentCreateRequest req) {
        long tenantId = req.getTenantId() != null ? req.getTenantId() : DEFAULT_TENANT_ID;
        String code = req.getIntentCode().trim().toUpperCase();
        assertCodeUnique(tenantId, code, null);
        AiIntent entity = new AiIntent();
        entity.setTenantId(tenantId);
        entity.setIntentCode(code);
        entity.setIntentName(req.getIntentName().trim());
        entity.setDescription(req.getDescription());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setEnabled(req.getEnabled() != null ? req.getEnabled() : YesNo.YES.getCode());
        baseMapper.insert(entity);
        log.info("Created ai_intent: id={}, tenantId={}, code={}", entity.getId(), tenantId, code);
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(AiIntentUpdateRequest req) {
        AiIntent entity = baseMapper.selectById(req.getId());
        if (entity == null) {
            throw new IllegalArgumentException("意图不存在: id=" + req.getId());
        }
        if (req.getIntentCode() != null) {
            String code = req.getIntentCode().trim().toUpperCase();
            assertCodeUnique(entity.getTenantId(), code, entity.getId());
            entity.setIntentCode(code);
        }
        if (req.getIntentName() != null) entity.setIntentName(req.getIntentName().trim());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());
        if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
        if (req.getEnabled() != null) entity.setEnabled(req.getEnabled());
        baseMapper.updateById(entity);
        log.info("Updated ai_intent: id={}", req.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enable(Long id) {
        AiIntent entity = require(id);
        entity.setEnabled(YesNo.YES.getCode());
        baseMapper.updateById(entity);
        log.info("Enabled ai_intent: id={}, code={}", id, entity.getIntentCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disable(Long id) {
        AiIntent entity = require(id);
        entity.setEnabled(YesNo.NO.getCode());
        baseMapper.updateById(entity);
        log.info("Disabled ai_intent: id={}, code={}", id, entity.getIntentCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AiIntent> listEnabledByTenant(Long tenantId) {
        long tid = tenantId != null ? tenantId : DEFAULT_TENANT_ID;
        return baseMapper.selectList(new LambdaQueryWrapper<AiIntent>()
                .eq(AiIntent::getTenantId, tid)
                .eq(AiIntent::getEnabled, YesNo.YES.getCode())
                .orderByAsc(AiIntent::getSortOrder)
                .orderByAsc(AiIntent::getIntentCode));
    }

    /**
     * 按主键加载意图。
     *
     * @param id 主键
     * @return 实体
     * @throws IllegalArgumentException 不存在
     */
    private AiIntent require(Long id) {
        AiIntent entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("意图不存在: id=" + id);
        }
        return entity;
    }

    /**
     * 校验同租户下意图编码唯一。
     *
     * @param tenantId  租户
     * @param code      编码（已大写）
     * @param excludeId 更新时排除自身主键；新建传 null
     */
    private void assertCodeUnique(Long tenantId, String code, Long excludeId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<AiIntent>()
                .eq(AiIntent::getTenantId, tenantId)
                .eq(AiIntent::getIntentCode, code)
                .ne(excludeId != null, AiIntent::getId, excludeId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("意图编码已存在: " + code);
        }
    }

    /**
     * 实体转响应 DTO。
     *
     * @param e 实体
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
        r.setEnabledName(org.deepstack.ai.kernel.enums.common.EnabledStatusEnum.labelOf(e.getEnabled()));
        r.setCreateTime(e.getCreateTime());
        r.setUpdateTime(e.getUpdateTime());
        return r;
    }
}
