package org.deepstack.ai.kernel.enums.agent;

/**
 * 智能体编排模式。
 */
public enum OrchestrateModeEnum {

    CHAT(0, "对话"),
    GRAPH(1, "编排图");

    private final int code;
    private final String label;

    OrchestrateModeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 返回 Code。 */
    public int getCode() {
        return code;
    }

    /** 返回 Label。 */
    public String getLabel() {
        return label;
    }

    /** 按数字码或原始值解析，无法识别时返回 null。 */
    public static OrchestrateModeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrchestrateModeEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /** 按数字码解析，无法识别时抛出异常。 */
    public static OrchestrateModeEnum ofRequired(Integer code) {
        OrchestrateModeEnum item = of(code);
        if (item == null) {
            throw new IllegalArgumentException("非法编排模式: " + code);
        }
        return item;
    }

    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {
        OrchestrateModeEnum item = of(code);
        return item != null ? item.label : null;
    }

    /** 整数码是否等于本枚举。 */
    public boolean matches(Integer code) {
        return code != null && this.code == code;
    }

    /** 是否 Graph。 */
    public boolean isGraph() {
        return this == GRAPH;
    }

    /** 是否 Chat。 */
    public boolean isChat() {
        return this == CHAT;
    }
}
