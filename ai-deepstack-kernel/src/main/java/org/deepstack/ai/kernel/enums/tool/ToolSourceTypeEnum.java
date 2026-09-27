package org.deepstack.ai.kernel.enums.tool;



/**

 * 工具来源类型。

 */

public enum ToolSourceTypeEnum {

    LOCAL(0, "本地"),

    MCP(1, "MCP");



    private final int code;

    private final String label;



    ToolSourceTypeEnum(int code, String label) {

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
    public static ToolSourceTypeEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (ToolSourceTypeEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static ToolSourceTypeEnum ofRequired(Integer code) {

        ToolSourceTypeEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法工具来源: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        ToolSourceTypeEnum item = of(code);

        return item != null ? item.label : null;

    }



    /** 是否 Mcp。 */
    public boolean isMcp() {

        return this == MCP;

    }

}


