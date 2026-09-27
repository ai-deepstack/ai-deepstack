package org.deepstack.ai.kernel.enums.tool;



/**

 * MCP 传输类型。

 */

public enum McpTransportEnum {

    SSE(0, "SSE"),

    STDIO(1, "STDIO");



    private final int code;

    private final String label;



    McpTransportEnum(int code, String label) {

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
    public static McpTransportEnum of(Integer code) {

        if (code == null) {

            return null;

        }

        for (McpTransportEnum item : values()) {

            if (item.code == code) {

                return item;

            }

        }

        return null;

    }



    /** 按数字码解析，无法识别时抛出异常。 */
    public static McpTransportEnum ofRequired(Integer code) {

        McpTransportEnum item = of(code);

        if (item == null) {

            throw new IllegalArgumentException("非法 MCP transport: " + code);

        }

        return item;

    }



    /** 返回码对应的中文标签；无法识别时返回 null 或空串。 */
    public static String labelOf(Integer code) {

        McpTransportEnum item = of(code);

        return item != null ? item.label : null;

    }

}


