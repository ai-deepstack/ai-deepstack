package org.deepstack.ai.runtime.spi;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 模型能力端口：业务键为 modelCode。
 */
public interface ModelPort {

    ChatModel getChatModel(String modelCode);

    String getApiModelName(String modelCode);
}
