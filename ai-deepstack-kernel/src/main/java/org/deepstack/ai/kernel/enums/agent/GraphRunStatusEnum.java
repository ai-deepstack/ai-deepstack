package org.deepstack.ai.kernel.enums.agent;

/**
 * 图 / 工作流运行状态（仅数字码）。
 */
public enum GraphRunStatusEnum {

    RUNNING(0, "运行中"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败"),
    CANCELLED(3, "已取消"),
    WAITING_HUMAN(4, "等待人工");

    private final int code;
    private final String label;

    GraphRunStatusEnum(int code, String label) {
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
    public static GraphRunStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (GraphRunStatusEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /** 按数字码解析，无法识别时抛出异常。 */
    public static GraphRunStatusEnum ofRequired(Integer code) {
        GraphRunStatusEnum item = of(code);
        if (item == null) {
            throw new IllegalArgumentException("非法运行状态: " + code);
        }
        return item;
    }

    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {
        GraphRunStatusEnum item = of(code);
        return item != null ? item.label : null;
    }

    /** 判断给定码是否等于当前枚举值。 */
    public boolean matches(Integer code) {
        return code != null && this.code == code;
    }

    /** 终态：成功 / 失败 / 取消。 */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
