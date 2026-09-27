package org.deepstack.ai.kernel.enums.knowledge;



/**

 * 知识文档解析状态。

 */

public enum DocParseStatusEnum {

    PENDING(0, "待解析"),

    PARSING(1, "解析中"),

    PARSED(2, "已解析"),

    FAILED(3, "失败");



    private final int code;

    private final String label;



    DocParseStatusEnum(int code, String label) {

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
    public static DocParseStatusEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (DocParseStatusEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static DocParseStatusEnum ofRequired(Integer code) {

        DocParseStatusEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法解析状态: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        DocParseStatusEnum item = of(code);

        return item != null ? item.label : null;

    }

}


