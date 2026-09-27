package org.deepstack.ai.agent.service.impl;


import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.agent.model.entity.AgentNodeType;
import org.deepstack.ai.agent.mapper.AgentNodeTypeMapper;
import org.deepstack.ai.agent.service.AgentNodeTypeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工作流节点类型定义服务实现
 *
 */
@Slf4j
@Service
public class AgentNodeTypeServiceImpl extends ServiceImpl<AgentNodeTypeMapper, AgentNodeType> implements AgentNodeTypeService {

    /** 应用级缓存（线程安全，refreshCache 时整体替换） */
    private volatile CopyOnWriteArrayList<AgentNodeType> cache;

    /**
     * 列出全部启用的节点类型（带本地缓存）。
     *
     * @return 启用节点类型列表
     */
    @Override
    public List<AgentNodeType> listEnabled() {
        if (cache != null) {
            log.debug("listEnabled: 命中缓存, size={}", cache.size());
            return cache;
        }
        log.info("listEnabled: 缓存未命中，从 DB 加载");
        List<AgentNodeType> list = baseMapper.selectList(
                new LambdaQueryWrapper<AgentNodeType>()
                        .eq(AgentNodeType::getEnabled, YesNo.YES.getCode())
                        .eq(AgentNodeType::getIsDel, 0)
                        .orderByAsc(AgentNodeType::getSortOrder)
        );
        cache = new CopyOnWriteArrayList<>(list);
        return cache;
    }

    /**
     * 按类型编码查询启用的节点类型。
     *
     * @param typeCode 类型编码
     * @return 节点类型；未找到返回 null
     */
    @Override
    public AgentNodeType getByTypeCode(String typeCode) {
        log.info("getByTypeCode: typeCode={}", typeCode);
        AgentNodeType found = listEnabled().stream()
                .filter(t -> typeCode.equals(t.getTypeCode()))
                .findFirst()
                .orElse(null);
        if (found == null) {
            log.warn("节点类型未找到: typeCode={}", typeCode);
        }
        return found;
    }

    /**
     * 分页查询节点类型。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param category 分类（可空）
     * @param enabled  启用状态（可空）
     * @return 分页结果
     */
    @Override
    public IPage<AgentNodeType> page(int pageNum, int pageSize, String category, Boolean enabled) {
        log.info("page: pageNum={}, pageSize={}, category={}, enabled={}",
                pageNum, pageSize, category, enabled);
        LambdaQueryWrapper<AgentNodeType> wrapper = new LambdaQueryWrapper<AgentNodeType>()
                .eq(category != null, AgentNodeType::getCategory, category)
                .eq(enabled != null, AgentNodeType::getEnabled, enabled)
                .orderByAsc(AgentNodeType::getSortOrder);
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 创建节点类型并刷新缓存。
     *
     * @param entity 节点类型实体
     * @return 新 ID
     */
    @Override
    public Long create(AgentNodeType entity) {
        log.info("create: typeCode={}, category={}", entity.getTypeCode(), entity.getCategory());
        baseMapper.insert(entity);
        refreshCache();
        log.info("Created agent node type: id={}, typeCode={}", entity.getId(), entity.getTypeCode());
        return entity.getId();
    }

    /**
     * 更新节点类型并刷新缓存。
     *
     * @param entity 节点类型实体
     */
    @Override
    public void update(AgentNodeType entity) {
        log.info("update: id={}", entity.getId());
        baseMapper.updateById(entity);
        refreshCache();
        log.info("Updated agent node type: id={}", entity.getId());
    }

    /**
     * 启用节点类型。
     *
     * @param id 节点类型 ID
     */
    @Override
    public void enable(Long id) {
        log.info("enable: id={}", id);
        AgentNodeType entity = baseMapper.selectById(id);
        if (entity == null) throw new IllegalArgumentException("节点类型不存在: id=" + id);
        entity.setEnabled(YesNo.YES.getCode());
        baseMapper.updateById(entity);
        refreshCache();
        log.info("Enabled agent node type: id={}", id);
    }

    /**
     * 禁用节点类型。
     *
     * @param id 节点类型 ID
     */
    @Override
    public void disable(Long id) {
        log.info("disable: id={}", id);
        AgentNodeType entity = baseMapper.selectById(id);
        if (entity == null) throw new IllegalArgumentException("节点类型不存在: id=" + id);
        entity.setEnabled(YesNo.NO.getCode());
        baseMapper.updateById(entity);
        refreshCache();
        log.info("Disabled agent node type: id={}", id);
    }

    /**
     * 按 ID 删除并刷新缓存。
     *
     * @param id 主键
     * @return 是否删除成功
     */
    @Override
    public boolean removeById(java.io.Serializable id) {
        log.info("removeById: id={}", id);
        boolean ok = super.removeById(id);
        refreshCache();
        if (!ok) {
            log.warn("removeById 未删除任何记录: id={}", id);
        }
        return ok;
    }

    /**
     * 从 DB 重载启用节点类型到本地缓存。
     */
    @Override
    public void refreshCache() {
        log.info("refreshCache: 开始刷新节点类型缓存");
        List<AgentNodeType> list = baseMapper.selectList(
                new LambdaQueryWrapper<AgentNodeType>()
                        .eq(AgentNodeType::getEnabled, YesNo.YES.getCode())
                        .eq(AgentNodeType::getIsDel, 0)
                        .orderByAsc(AgentNodeType::getSortOrder)
        );
        cache = new CopyOnWriteArrayList<>(list);
        log.info("Refreshed agent node type cache: {} entries", list.size());
    }
}
