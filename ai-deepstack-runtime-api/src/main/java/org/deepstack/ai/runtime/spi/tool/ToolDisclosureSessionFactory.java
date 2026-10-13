package org.deepstack.ai.runtime.spi.tool;

import java.util.Collection;

/**
 * 按 Agent 绑定允许集打开渐进披露会话。
 */
public interface ToolDisclosureSessionFactory {

    /**
     * @param allowToolCodes 来自 Agent 绑定 {@code listEnabledToolCodes}，不可传「全平台」
     */
    ToolDisclosureSession open(Collection<String> allowToolCodes);
}
