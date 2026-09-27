package org.deepstack.ai.kernel.enums.common;

import java.util.Objects;

/**
 * 是否枚举：0=否、1=是。
 */
public enum YesNo {
    NO(0, "否"),
    YES(1, "是");

    private final int code;
    private final String label;

    YesNo(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** @return 数值码（0/1） */
    public int getCode() {
        return code;
    }

    /** @return 中文标签 */
    public String getLabel() {
        return label;
    }

    /** 是否为 YES。 */
    public boolean isYes() {
        return this == YES;
    }

    /** 是否为 NO。 */
    public boolean isNo() {
        return this == NO;
    }

    /**
     * 按 code 解析；未知或 null 返回 null。
     *
     * @param code 0/1，可为 null
     */
    public static YesNo of(Integer code) {
        if (code == null) {
            return null;
        }
        for (YesNo item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按 code 解析；非法值抛 {@link IllegalArgumentException}。
     */
    public static YesNo ofRequired(Integer code) {
        YesNo yesNo = of(code);
        if (yesNo == null) {
            throw new IllegalArgumentException("非法 YesNo 值: " + code);
        }
        return yesNo;
    }

    /**
     * code 是否等于 YES(1)。
     */
    public static boolean isYes(Integer code) {
        return Objects.equals(code, YES.code);
    }

    /**
     * code 是否视为否（非 YES）。
     */
    public static boolean isNo(Integer code) {
        return !isYes(code);
    }

    /**
     * boolean → YesNo code（true→1，false→0）。
     */
    public static int codeOf(boolean value) {
        return value ? YES.code : NO.code;
    }

    /**
     * 取中文标签；未知或 null 返回 null。
     *
     * @param code 0/1
     * @return 否/是；无法解析时 null
     */
    public static String labelOf(Integer code) {
        YesNo item = of(code);
        return item != null ? item.label : null;
    }
}
