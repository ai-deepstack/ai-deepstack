package org.deepstack.ai.kernel.enums.knowledge;



/**

 * 知识文档向量化状态。

 */

public enum DocEmbedStatusEnum {

    PENDING(0, "待向量化"),

    EMBEDDING(1, "向量化中"),

    EMBEDDED(2, "已向量化"),

    FAILED(3, "失败");



    private final int code;

    private final String label;



    DocEmbedStatusEnum(int code, String label) {

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
    public static DocEmbedStatusEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (DocEmbedStatusEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static DocEmbedStatusEnum ofRequired(Integer code) {

        DocEmbedStatusEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法向量化状态: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        DocEmbedStatusEnum item = of(code);

        return item != null ? item.label : null;

    }

}


