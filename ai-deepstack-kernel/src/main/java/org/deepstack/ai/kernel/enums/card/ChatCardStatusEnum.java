package org.deepstack.ai.kernel.enums.card;

/**
 * 聊天卡片状态。
 */
public enum ChatCardStatusEnum {
    PENDING(0, "待处理"),
    CONFIRMED(1, "已确认"),
    EDITED(2, "已编辑"),
    REJECTED(3, "已拒绝"),
    EXPIRED(4, "已过期");

    private final int code;
    private final String label;

    ChatCardStatusEnum(int code, String label) {
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
    public static ChatCardStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ChatCardStatusEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /** 按数字码解析，无法识别时抛出异常。 */
    public static ChatCardStatusEnum ofRequired(Integer code) {
        ChatCardStatusEnum item = of(code);
        if (item == null) {
            throw new IllegalArgumentException("非法卡片状态: " + code);
        }
        return item;
    }

    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {
        ChatCardStatusEnum item = of(code);
        return item != null ? item.label : null;
    }
}
