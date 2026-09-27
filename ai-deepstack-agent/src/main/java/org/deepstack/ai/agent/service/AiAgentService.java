package org.deepstack.ai.agent.service;



import org.deepstack.ai.agent.model.dto.request.AiAgentCreateRequest;

import org.deepstack.ai.agent.model.dto.request.AiAgentPageRequest;

import org.deepstack.ai.agent.model.dto.request.AiAgentUpdateRequest;

import org.deepstack.ai.agent.model.entity.AiAgent;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.baomidou.mybatisplus.extension.service.IService;



import java.util.List;



/**

 * 智能体配置服务。

 * <p>

 * 负责智能体 CRUD、启停、知识库绑定，以及按编码查询启用态配置。

 * </p>

 */

public interface AiAgentService extends IService<AiAgent> {



    /**

     * 按智能体编码查询已启用的智能体。

     *

     * @param agentCode 智能体编码

     * @return 启用态实体；未找到返回 {@code null}

     */

    AiAgent getByAgentCode(String agentCode);



    /**

     * 列出全部已启用的智能体（按 id 升序）。

     *

     * @return 启用智能体列表

     */

    List<AiAgent> listEnabled();



    /**

     * 查询智能体已绑定的知识库编码列表（按优先级）。

     *

     * @param agentId 智能体主键

     * @return 知识库编码列表；无绑定时返回空列表

     */

    List<String> getKnowledgeBaseCodes(Long agentId);



    /**

     * 全量重绑智能体与知识库关系（先删后插，列表顺序即优先级）。

     *

     * @param agentId            智能体主键

     * @param knowledgeBaseCodes 知识库编码列表；空或 null 表示清空绑定

     */

    void bindKnowledgeBases(Long agentId, List<String> knowledgeBaseCodes);



    /**

     * 分页查询智能体。

     *

     * @param req 分页与筛选条件（关键字、编码、模型、启用状态等）

     * @return 实体分页结果

     */

    IPage<AiAgent> page(AiAgentPageRequest req);



    /**

     * 新建智能体（自动生成唯一 agentCode，可选同步绑定知识库）。

     *

     * @param req 创建请求

     * @return 新建记录主键

     */

    Long create(AiAgentCreateRequest req);



    /**

     * 更新智能体配置；若请求携带知识库列表则同步重绑。

     *

     * @param req 更新请求

     * @throws IllegalArgumentException 智能体不存在

     */

    void update(AiAgentUpdateRequest req);



    /**

     * 启用智能体。

     *

     * @param id 智能体主键

     * @throws IllegalArgumentException 智能体不存在

     */

    void enable(Long id);



    /**

     * 停用智能体，并清理智能体缓存。

     *

     * @param id 智能体主键

     * @throws IllegalArgumentException 智能体不存在

     */

    void disable(Long id);



    /**

     * 删除智能体及其知识库绑定关系。

     *

     * @param id 智能体主键

     */

    void deleteById(Long id);



    /**

     * 分页查询模板智能体（{@code template=1}）。

     *

     * @param req 分页与筛选条件

     * @return 模板分页结果

     */

    IPage<AiAgent> pageTemplates(AiAgentPageRequest req);



    /**

     * 将现有智能体另存为模板（新行 {@code template=1}、停用）。

     *

     * @param sourceId  源智能体主键

     * @param agentCode 新编码（可空则自动生成）

     * @param agentName 新名称（可空则沿用源名称）

     * @return 新模板主键

     */

    Long saveAsTemplate(Long sourceId, String agentCode, String agentName);



    /**

     * 从模板复制为普通智能体（{@code template=0}、停用；发布字段清空）。

     *

     * @param templateId 模板主键

     * @param agentCode  新编码（可空则自动生成）

     * @param agentName  新名称（可空则沿用模板名称）

     * @return 新智能体主键

     */

    Long createFromTemplate(Long templateId, String agentCode, String agentName);

}


