package org.deepstack.ai.runtime.spi;

/**
 * 卡片能力端口：不依赖 card 模块类型。
 * <p>
 * 流式推送仍走 {@code AgentNodeContext} 的 {@code Consumer&lt;Object&gt;}（业务侧卡片对象）；
 * 本端口用于产出后的应用侧钩子（落库、审计等），入参为 {@link EmittedCard}。
 * </p>
 */
public interface CardPort {

    /**
     * 卡片已推入流之后的应用侧钩子。
     *
     * @param card 契约对象，可空
     */
    default void afterEmitted(EmittedCard card) {
        // no-op
    }
}
