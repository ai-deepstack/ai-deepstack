package org.deepstack.ai.kernel.enums.knowledge;

/**
 * 知识文档写图状态。
 */
public enum DocGraphStatusEnum {

    PENDING(0, "未处理"),
    WRITING(1, "写入中"),
    WRITTEN(2, "已写入"),
    FAILED(3, "失败"),
    SKIPPED(4, "跳过");

    private final int code;
    private final String label;

    DocGraphStatusEnum(int code, String label) {
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
    public static DocGraphStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (DocGraphStatusEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /** 按数字码解析，无法识别时抛出异常。 */
    public static DocGraphStatusEnum ofRequired(Integer code) {
        DocGraphStatusEnum item = of(code);
        if (item == null) {
            throw new IllegalArgumentException("非法写图状态: " + code);
        }
        return item;
    }

    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {
        DocGraphStatusEnum item = of(code);
        return item != null ? item.label : null;
    }
}
