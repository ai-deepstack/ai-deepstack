package org.deepstack.ai.kernel.enums.knowledge;



/**

 * 知识文档来源类型。

 */

public enum DocSourceTypeEnum {

    MANUAL(0, "手工录入"),

    FILE(1, "文件"),

    URL(2, "URL");



    private final int code;

    private final String label;



    DocSourceTypeEnum(int code, String label) {

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
    public static DocSourceTypeEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (DocSourceTypeEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static DocSourceTypeEnum ofRequired(Integer code) {

        DocSourceTypeEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法文档来源: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        DocSourceTypeEnum item = of(code);

        return item != null ? item.label : null;

    }

}


