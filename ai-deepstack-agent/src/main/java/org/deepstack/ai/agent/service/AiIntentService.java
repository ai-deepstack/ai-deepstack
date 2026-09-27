package org.deepstack.ai.agent.service;



import org.deepstack.ai.agent.model.dto.request.AiIntentCreateRequest;

import org.deepstack.ai.agent.model.dto.request.AiIntentPageRequest;

import org.deepstack.ai.agent.model.dto.request.AiIntentUpdateRequest;

import org.deepstack.ai.agent.model.dto.response.AiIntentResponse;

import org.deepstack.ai.agent.model.entity.AiIntent;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.baomidou.mybatisplus.extension.service.IService;



import java.util.List;



/**

 * 租户级意图字典服务。

 * <p>

 * 管理意图编码/名称的 CRUD、启停，以及运行时按租户拉取已启用意图。

 * </p>

 */

public interface AiIntentService extends IService<AiIntent> {



    /** 默认租户（平台尚未接入真实租户时使用） */

    long DEFAULT_TENANT_ID = 0L;



    /**

     * 分页查询意图（默认租户为 {@link #DEFAULT_TENANT_ID}）。

     *

     * @param req 分页与筛选条件

     * @return 实体分页结果

     */

    IPage<AiIntent> page(AiIntentPageRequest req);



    /**

     * 按主键查询意图详情。

     *

     * @param id 意图主键

     * @return 详情 DTO；不存在返回 {@code null}

     */

    AiIntentResponse getDetail(Long id);



    /**

     * 新建意图（编码同租户唯一，自动转大写）。

     *

     * @param req 创建请求

     * @return 新建记录主键

     * @throws IllegalArgumentException 编码已存在等参数问题

     */

    Long create(AiIntentCreateRequest req);



    /**

     * 更新意图；若修改编码则校验同租户唯一。

     *

     * @param req 更新请求

     * @throws IllegalArgumentException 意图不存在或编码冲突

     */

    void update(AiIntentUpdateRequest req);



    /**

     * 启用意图。

     *

     * @param id 意图主键

     * @throws IllegalArgumentException 意图不存在

     */

    void enable(Long id);



    /**

     * 停用意图。

     *

     * @param id 意图主键

     * @throws IllegalArgumentException 意图不存在

     */

    void disable(Long id);



    /**

     * 某租户下已启用的意图，按 sortOrder、intentCode 升序。

     *

     * @param tenantId 租户 ID；null 时使用 {@link #DEFAULT_TENANT_ID}

     * @return 启用意图列表

     */

    List<AiIntent> listEnabledByTenant(Long tenantId);

}


