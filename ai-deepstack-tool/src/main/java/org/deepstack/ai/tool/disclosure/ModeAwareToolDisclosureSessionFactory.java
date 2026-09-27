package org.deepstack.ai.tool.disclosure;

import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.runtime.spi.ToolPort;
import org.deepstack.ai.runtime.spi.tool.PassthroughToolDisclosureSessionFactory;
import org.deepstack.ai.runtime.spi.tool.ToolDescriptorIndex;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSession;
import org.deepstack.ai.runtime.spi.tool.ToolDisclosureSessionFactory;
import org.deepstack.ai.tool.config.DeepstackToolProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 按配置选择全量披露或渐进式披露（元工具 + search/load）。
 * <p>优先读 sys_config {@code tools.disclosure-mode}，否则回退 yml。</p>
 */
@Slf4j
@Component
@Primary
public class ModeAwareToolDisclosureSessionFactory implements ToolDisclosureSessionFactory {

    private final ToolPort toolPort;
    private final ToolDescriptorIndex toolDescriptorIndex;
    private final SysConfigPort sysConfigPort;
    private final DeepstackToolProperties properties;
    private final PassthroughToolDisclosureSessionFactory passthrough;

    /**
     * @param toolDescriptorIndex 工具摘要索引
     * @param toolPort            工具解析端口
     * @param sysConfigPort       系统配置
     * @param properties          yml 兜底配置
     */
    public ModeAwareToolDisclosureSessionFactory(ToolDescriptorIndex toolDescriptorIndex,
                                                 ToolPort toolPort,
                                                 SysConfigPort sysConfigPort,
                                                 DeepstackToolProperties properties) {
        this.toolDescriptorIndex = toolDescriptorIndex;
        this.toolPort = toolPort;
        this.sysConfigPort = sysConfigPort;
        this.properties = properties;
        this.passthrough = new PassthroughToolDisclosureSessionFactory(toolDescriptorIndex, toolPort);
    }

    /**
     * 打开一轮工具披露会话。
     *
     * @param allowToolCodes 智能体绑定的允许工具编码集合
     * @return 全量或渐进式披露会话
     */
    @Override
    public ToolDisclosureSession open(Collection<String> allowToolCodes) {
        Collection<String> allow = allowToolCodes == null ? List.of() : allowToolCodes;
        int size = (int) allow.stream().filter(c -> c != null && !c.isBlank()).count();
        log.info("ToolDisclosure open: allowSize={}, progressive={}", size, isProgressive());

        if (!isProgressive()) {
            return passthrough.open(allow);
        }

        int fullBelow = sysConfigPort.getInt(SysConfigKeys.TOOLS_PROGRESSIVE_FULL_BELOW,
                properties.getProgressiveFullBelow());
        if (fullBelow > 0 && size > 0 && size <= fullBelow) {
            log.debug("disclosure progressive but allowSize={} <= fullBelow={}, use passthrough",
                    size, fullBelow);
            return passthrough.open(allow);
        }

        log.debug("disclosure progressive: allowSize={}", size);
        return new ProgressiveToolDisclosureSession(
                toolPort,
                toolDescriptorIndex.view(allow),
                allow,
                sysConfigPort,
                properties);
    }

    /** 是否启用渐进式披露（sys_config 优先于 yml）。 */
    private boolean isProgressive() {
        String mode = sysConfigPort.getString(SysConfigKeys.TOOLS_DISCLOSURE_MODE);
        if (mode == null || mode.isBlank()) {
            return properties.isProgressive();
        }
        return "progressive".equalsIgnoreCase(mode.trim());
    }
}

