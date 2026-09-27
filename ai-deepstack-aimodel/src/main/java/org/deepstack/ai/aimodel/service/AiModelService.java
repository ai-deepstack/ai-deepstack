package org.deepstack.ai.aimodel.service;

import org.deepstack.ai.aimodel.model.dto.request.AiModelCreateRequest;
import org.deepstack.ai.aimodel.model.dto.request.AiModelPageRequest;
import org.deepstack.ai.aimodel.model.dto.request.AiModelUpdateRequest;
import org.deepstack.ai.aimodel.model.entity.AiModel;
import org.deepstack.ai.kernel.model.AppUserInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * AI 模型配置服务（CHAT + EMBEDDING）。
 * <p>管理侧查询按可见性过滤；运行时 {@link #getByModelCode} 仍按编码取启用模型。</p>
 */
public interface AiModelService extends IService<AiModel> {

    // ===== 运行时查询 =====

    /**
     * 按 modelCode 获取已启用模型配置。
     *
     * @param modelCode 模型编码
     * @return 启用模型；不存在返回 null
     */
    AiModel getByModelCode(String modelCode);

    /**
     * 查询所有启用的模型（无可见性过滤；供内部运行时）。
     *
     * @return 启用模型列表
     */
    List<AiModel> listEnabled();

    /**
     * 查询所有启用的指定类型模型（无可见性过滤；供内部运行时）。
     *
     * @param modelType 见 ModelTypeEnum
     * @return 启用模型列表
     */
    List<AiModel> listEnabledByType(Integer modelType);

    /**
     * 按可见性过滤的启用模型下拉（公共 + 自己的私有；管理员全部）。
     *
     * @param modelType 可选类型过滤
     * @param user      当前登录用户
     * @return 可见启用模型
     */
    List<AiModel> listVisibleEnabled(Integer modelType, AppUserInfo user);

    // ===== CRUD =====

    /**
     * 分页查询模型（按可见性过滤）。
     *
     * @param req  分页查询条件
     * @param user 当前登录用户
     * @return 分页结果
     */
    IPage<AiModel> page(AiModelPageRequest req, AppUserInfo user);

    /**
     * 新建模型。
     *
     * @param req  新建请求
     * @param user 当前登录用户
     * @return 新记录 id
     */
    Long create(AiModelCreateRequest req, AppUserInfo user);

    /**
     * 修改模型（不含 apiKey）。
     *
     * @param req  修改请求
     * @param user 当前登录用户
     */
    void update(AiModelUpdateRequest req, AppUserInfo user);

    /**
     * 单独修改 apiKey。
     *
     * @param id     模型 id
     * @param apiKey 新的 apiKey 明文
     * @param user   当前登录用户
     */
    void updateApiKey(Long id, String apiKey, AppUserInfo user);

    /**
     * 启用模型（须有写权限）。
     *
     * @param id   模型 id
     * @param user 当前登录用户
     */
    void enable(Long id, AppUserInfo user);

    /**
     * 禁用模型（须有写权限）。
     *
     * @param id   模型 id
     * @param user 当前登录用户
     */
    void disable(Long id, AppUserInfo user);

    /**
     * 删除模型（须有写权限）。
     *
     * @param id   模型 id
     * @param user 当前登录用户
     */
    void delete(Long id, AppUserInfo user);

    /**
     * 当前用户是否可查看脱敏后的 API Key（管理员或私有所有者；公共模型对可见用户返回脱敏）。
     *
     * @param model 模型实体
     * @param user  当前用户
     * @return true 可返回脱敏 key
     */
    boolean canSeeApiKey(AiModel model, AppUserInfo user);
}
