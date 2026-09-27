package org.deepstack.ai.runtime.spi.tool;

import org.deepstack.ai.runtime.spi.ToolPort;
import lombok.RequiredArgsConstructor;

import java.util.Collection;

/**
 * Noop/兼容 Factory：disclosureMode=OFF，全量 resolve 允许集。
 */
@RequiredArgsConstructor
public class PassthroughToolDisclosureSessionFactory implements ToolDisclosureSessionFactory {

    private final ToolDescriptorIndex toolDescriptorIndex;
    private final ToolPort toolPort;

    /** 打开一次工具披露会话。 */
    @Override
    public ToolDisclosureSession open(Collection<String> allowToolCodes) {
        Collection<String> allow = allowToolCodes == null ? java.util.List.of() : allowToolCodes;
        ToolDescriptorIndex view = toolDescriptorIndex.view(allow);
        return new PassthroughToolDisclosureSession(toolPort, view, allow);
    }
}
