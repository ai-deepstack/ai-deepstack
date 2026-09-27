package org.deepstack.ai.infra.spi;

import org.deepstack.ai.infra.llm.AiChatClientFactory;
import org.deepstack.ai.runtime.spi.ModelPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * {@link ModelPort} 适配：按 modelCode 委托 {@link AiChatClientFactory}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelPortAdapter implements ModelPort {

    private final AiChatClientFactory aiChatClientFactory;

    /**
     * 按模型编码获取 Chat 客户端。
     *
     * @param modelCode 模型编码
     * @return Spring AI ChatModel
     */
    @Override
    public ChatModel getChatModel(String modelCode) {
        log.debug("ModelPortAdapter.getChatModel: modelCode={}", modelCode);
        return aiChatClientFactory.getChatModel(modelCode);
    }

    /**
     * 按模型编码获取供应商侧模型名。
     *
     * @param modelCode 模型编码
     * @return apiModelName
     */
    @Override
    public String getApiModelName(String modelCode) {
        log.debug("ModelPortAdapter.getApiModelName: modelCode={}", modelCode);
        return aiChatClientFactory.getApiModelName(modelCode);
    }
}
