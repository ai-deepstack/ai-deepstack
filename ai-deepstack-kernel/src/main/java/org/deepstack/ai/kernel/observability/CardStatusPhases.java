package org.deepstack.ai.kernel.observability;

/**
 * 卡片生命周期 phase（CARD_STATUS / card_status SSE）。
 */
public final class CardStatusPhases {

    /** 卡片生成中。 */
    public static final String GENERATING = "generating";
    /** 卡片生成失败。 */
    public static final String FAILED = "failed";

    /** 禁止实例化。 */
    private CardStatusPhases() {
    }
}
