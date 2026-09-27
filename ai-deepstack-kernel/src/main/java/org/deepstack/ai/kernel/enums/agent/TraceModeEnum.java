package org.deepstack.ai.kernel.enums.agent;

/**
 * 图执行追踪模式（仅数字码）。
 */
public enum TraceModeEnum {

    NONE(0, "关闭"),
    RECORD(1, "记录"),
    STREAM(2, "流式");

    private final int code;
    private final String label;

    TraceModeEnum(int code, String label) {
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
    public static TraceModeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (TraceModeEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /** 按数字码解析，无法识别时抛出异常。 */
    public static TraceModeEnum ofRequired(Integer code) {
        TraceModeEnum item = of(code);
        if (item == null) {
            throw new IllegalArgumentException("非法追踪模式: " + code);
        }
        return item;
    }

    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {
        TraceModeEnum item = of(code);
        return item != null ? item.label : null;
    }

    /** 判断给定码是否等于当前枚举值。 */
    public boolean matches(Integer code) {
        return code != null && this.code == code;
    }
}
