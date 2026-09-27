package org.deepstack.ai.kernel.enums.card;



/**

 * 卡片用户动作类型。

 */

public enum CardActionTypeEnum {

    CONFIRM(0, "确认"),

    EDIT(1, "编辑"),

    REJECT(2, "拒绝");



    private final int code;

    private final String label;



    CardActionTypeEnum(int code, String label) {

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



    /** 图 state / HITL wire 值（小写 confirm/edit/reject）。 */

    public String wireValue() {

        return name().toLowerCase();

    }



    /** 按数字码或原始值解析，无法识别时返回 null。 */
    public static CardActionTypeEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (CardActionTypeEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static CardActionTypeEnum ofRequired(Integer code) {

        CardActionTypeEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法卡片动作: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        CardActionTypeEnum item = of(code);

        return item != null ? item.label : null;

    }

}


