package org.deepstack.ai.runtime.spi;

import com.alibaba.fastjson2.JSON;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 运行时卡片载荷（SPI 契约对象，不依赖 card 模块）。
 * <p>
 * 流式边界仍可序列化为 JSON；Port 钩子使用本类型。
 * </p>
 */
@Data
public class EmittedCard implements Serializable {

    private String cardId;
    private String cardType;
    private String title;
    private String conversationId;
    private String userId;
    private String threadId;
    private String checkpointId;
    private Map<String, Object> payload;
    private List<Map<String, Object>> actions;
    private String parentCardIdStr;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    /**
     * 从工具/业务侧对象转为契约对象（属性名对齐即可，如 ChatCard）。
     */
    public static EmittedCard from(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof EmittedCard emitted) {
            return emitted;
        }
        return JSON.parseObject(JSON.toJSONString(source), EmittedCard.class);
    }
}
