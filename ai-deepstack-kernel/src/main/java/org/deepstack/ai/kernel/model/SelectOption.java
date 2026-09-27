package org.deepstack.ai.kernel.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 下拉框选项（仅 value / label，供选择器复用，勿复用分页 list DTO）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectOption implements Serializable {

    /** 选项值（业务 code，或绑定场景下的 id 字符串） */
    private String value;

    /** 展示文案 */
    private String label;

    /** 按数字码或原始值解析，无法识别时返回 null。 */
    public static SelectOption of(String value, String label) {
        return new SelectOption(value, label);
    }
}
