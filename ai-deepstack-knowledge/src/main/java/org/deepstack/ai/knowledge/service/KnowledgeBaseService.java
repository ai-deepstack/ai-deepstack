package org.deepstack.ai.knowledge.service;

import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBaseCreateRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBasePageRequest;
import org.deepstack.ai.knowledge.model.dto.request.KnowledgeBaseUpdateRequest;
import org.deepstack.ai.knowledge.model.entity.KnowledgeBase;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 知识库服务：元信息 CRUD、分片/检索参数、Embedding 模型绑定校验。
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 按 id 获取已启用知识库（与 {@link IService#getById} 区分）。
     *
     * @param id 知识库主键
     * @return 启用态实体；不存在或未启用返回 null
     */
    KnowledgeBase getEnabledById(Long id);

    /**
     * 按 baseCode 获取已启用知识库。
     *
     * @param baseCode 知识库编码
     * @return 启用态实体；不存在或未启用返回 null
     */
    KnowledgeBase getByBaseCode(String baseCode);

    /**
     * 按多个 baseCode 批量查询已启用知识库（一次 IN）。
     *
     * @param baseCodes 知识库编码列表
     * @return 启用态列表（顺序不保证与入参一致）
     */
    List<KnowledgeBase> listByBaseCodes(List<String> baseCodes);

    /**
     * 查询全部已启用知识库。
     *
     * @return 启用列表
     */
    List<KnowledgeBase> listEnabled();

    /**
     * 分页查询知识库（含禁用）。
     *
     * @param req 分页与筛选
     * @return 分页结果
     */
    IPage<KnowledgeBase> page(KnowledgeBasePageRequest req);

    /**
     * 新建知识库（校验 Embedding 模型类型）。
     *
     * @param req 创建请求
     * @return 新记录 id
     */
    Long create(KnowledgeBaseCreateRequest req);

    /**
     * 修改知识库元信息。
     *
     * @param req 更新请求
     */
    void update(KnowledgeBaseUpdateRequest req);

    /**
     * 启用知识库。
     *
     * @param id 主键
     */
    void enable(Long id);

    /**
     * 禁用知识库。
     *
     * @param id 主键
     */
    void disable(Long id);

    /**
     * 逻辑删除知识库（文档与分片级联由调用方控制）。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
