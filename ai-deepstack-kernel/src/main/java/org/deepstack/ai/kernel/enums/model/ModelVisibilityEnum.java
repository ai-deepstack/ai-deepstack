package org.deepstack.ai.kernel.enums.model;

/**
 * AI 模型可见性：公共 / 私有。
 */
public enum ModelVisibilityEnum {

    /** 公共：全员可见 */
    PUBLIC(0, "公共"),

    /** 私有：仅所有者与管理员可见 */
    PRIVATE(1, "私有");

    private final int code;
    private final String label;

    ModelVisibilityEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 返回数字码。 */
    public int getCode() {
        return code;
    }

    /** 返回中文标签。 */
    public String getLabel() {
        return label;
    }

    /**
     * 按数字码解析；无法识别时返回 null。
     *
     * @param code 可见性码
     * @return 枚举或 null
     */
    public static ModelVisibilityEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ModelVisibilityEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按数字码解析；无法识别时抛异常。
     *
     * @param code 可见性码
     * @return 枚举
     */
    public static ModelVisibilityEnum ofRequired(Integer code) {
        ModelVisibilityEnum item = of(code);
        if (item == null) {
            throw new IllegalArgumentException("非法模型可见性: " + code);
        }
        return item;
    }

    /**
     * 返回码对应的中文标签；无法识别时返回 null。
     *
     * @param code 可见性码
     * @return 标签
     */
    public static String labelOf(Integer code) {
        ModelVisibilityEnum item = of(code);
        return item != null ? item.label : null;
    }

    /**
     * 整数码是否等于本枚举。
     *
     * @param code 可见性码
     * @return 是否匹配
     */
    public boolean matches(Integer code) {
        return code != null && this.code == code;
    }
}
