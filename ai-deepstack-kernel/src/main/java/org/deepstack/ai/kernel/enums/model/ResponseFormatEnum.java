package org.deepstack.ai.kernel.enums.model;



/**

 * 模型响应格式。

 */

public enum ResponseFormatEnum {

    TEXT(0, "文本"),

    JSON(1, "JSON"),

    JSON_SCHEMA(2, "JSON Schema");



    private final int code;

    private final String label;



    ResponseFormatEnum(int code, String label) {

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
    public static ResponseFormatEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (ResponseFormatEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static ResponseFormatEnum ofRequired(Integer code) {

        ResponseFormatEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法响应格式: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        ResponseFormatEnum item = of(code);

        return item != null ? item.label : null;

    }

}


