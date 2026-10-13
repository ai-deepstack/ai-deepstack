package org.deepstack.ai.agent.service;

import org.deepstack.ai.agent.model.dto.request.AiAgentCreateRequest;
import org.deepstack.ai.agent.model.dto.request.AiAgentPageRequest;
import org.deepstack.ai.agent.model.dto.request.AiAgentUpdateRequest;
import org.deepstack.ai.agent.model.entity.AiAgent;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * Agent 配置服务。
 * <p>
 * 负责 Agent CRUD、启停、知识库绑定，以及按编码查询启用态配置。
 * 复用通过「复制创建」（{@code sourceAgentId}），不做模板实体。
 * </p>
 */
public interface AiAgentService extends IService<AiAgent> {

    /**
     * 按 Agent 编码查询已启用的 Agent。
     *
     * @param agentCode Agent 编码
     * @return 启用态实体；未找到返回 {@code null}
     */
    AiAgent getByAgentCode(String agentCode);

    /**
     * 列出全部已启用的 Agent（按 id 升序）。
     *
     * @return 启用 Agent 列表
     */
    List<AiAgent> listEnabled();

    /**
     * 查询 Agent 已绑定的知识库编码列表（按优先级）。
     *
     * @param agentId Agent 主键
     * @return 知识库编码列表；无绑定时返回空列表
     */
    List<String> getKnowledgeBaseCodes(Long agentId);

    /**
     * 全量重绑 Agent 与知识库关系（先删后插，列表顺序即优先级）。
     *
     * @param agentId            Agent 主键
     * @param knowledgeBaseCodes 知识库编码列表；空或 null 表示清空绑定
     */
    void bindKnowledgeBases(Long agentId, List<String> knowledgeBaseCodes);

    /**
     * 分页查询 Agent。
     *
     * @param req 分页与筛选条件（关键字、编码、模型、启用状态等）
     * @return 实体分页结果
     */
    IPage<AiAgent> page(AiAgentPageRequest req);

    /**
     * 新建 Agent。
     * <p>
     * 编码由服务端生成；名称全局唯一。若 {@code sourceAgentId} 有值，从源复制配置壳与绑定，
     * 再用请求字段覆盖，新副本默认停用。
     * </p>
     *
     * @param req 创建请求
     * @return 新建记录主键
     */
    Long create(AiAgentCreateRequest req);

    /**
     * 更新 Agent 配置；若请求携带知识库列表则同步重绑。
     * <p>改名时校验名称全局唯一（排除自身）。</p>
     *
     * @param req 更新请求
     * @throws IllegalArgumentException Agent 不存在
     */
    void update(AiAgentUpdateRequest req);

    /**
     * 启用 Agent。
     *
     * @param id Agent 主键
     * @throws IllegalArgumentException Agent 不存在
     */
    void enable(Long id);

    /**
     * 停用 Agent，并清理 Agent 缓存。
     *
     * @param id Agent 主键
     * @throws IllegalArgumentException Agent 不存在
     */
    void disable(Long id);

    /**
     * 删除 Agent 及其知识库绑定关系。
     *
     * @param id Agent 主键
     */
    void deleteById(Long id);
}
