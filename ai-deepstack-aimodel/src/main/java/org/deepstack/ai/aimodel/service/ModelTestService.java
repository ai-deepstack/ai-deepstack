package org.deepstack.ai.aimodel.service;

import org.deepstack.ai.aimodel.model.dto.response.ModelTestResult;
import org.deepstack.ai.aimodel.model.entity.AiModel;

/**
 * 模型连通性测试：用与运行时相同的参数构建客户端并发起一次最小调用。
 * <p>实现在 {@code ai-deepstack-infra}（依赖本模块），避免 aimodel 反向依赖 LLM 客户端。</p>
 */
public interface ModelTestService {

    /**
     * 测试模型接口是否可用。
     *
     * @param config 模型配置（可为未保存的临时对象）
     * @return 测试结果，失败时 {@code ok=false} 并带原因
     */
    ModelTestResult test(AiModel config);
}
