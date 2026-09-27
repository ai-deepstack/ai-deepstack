package org.deepstack.ai.kernel.enums.common;

/**
 * 资源启用状态：0=停用、1=启用。
 */
public enum EnabledStatusEnum {
    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    private final int code;
    private final String label;

    EnabledStatusEnum(int code, String label) {
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
    public static EnabledStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (EnabledStatusEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /** 按数字码解析，无法识别时抛出异常。 */
    public static EnabledStatusEnum ofRequired(Integer code) {
        EnabledStatusEnum item = of(code);
        if (item == null) {
            throw new IllegalArgumentException("非法启用状态: " + code);
        }
        return item;
    }

    /** 是否 Enabled。 */
    public static boolean isEnabled(Integer code) {
        return code != null && code == ENABLED.code;
    }

    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {
        EnabledStatusEnum item = of(code);
        return item != null ? item.label : null;
    }
}
