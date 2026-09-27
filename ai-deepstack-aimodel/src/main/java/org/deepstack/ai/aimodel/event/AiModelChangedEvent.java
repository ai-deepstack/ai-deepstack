package org.deepstack.ai.aimodel.event;

/**
 * 模型配置变更事件：更新 / 改密钥 / 启停后发布，供 LLM 客户端缓存失效。
 *
 * @param modelCode ai_model.model_code；空白则表示需全量清理
 */
public record AiModelChangedEvent(String modelCode) {
}
