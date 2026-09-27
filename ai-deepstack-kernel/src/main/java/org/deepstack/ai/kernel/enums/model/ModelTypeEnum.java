package org.deepstack.ai.kernel.enums.model;



/**

 * AI 模型类型：CHAT / EMBEDDING。

 */

public enum ModelTypeEnum {

    CHAT(0, "对话"),

    EMBEDDING(1, "向量");



    private final int code;

    private final String label;



    ModelTypeEnum(int code, String label) {

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
    public static ModelTypeEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (ModelTypeEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static ModelTypeEnum ofRequired(Integer code) {

        ModelTypeEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法模型类型: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        ModelTypeEnum item = of(code);

        return item != null ? item.label : null;

    }

}


