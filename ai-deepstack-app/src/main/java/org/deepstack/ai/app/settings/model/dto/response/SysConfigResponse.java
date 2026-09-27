package org.deepstack.ai.app.settings.model.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统配置列表项（{@code GET /api/settings}）。
 */
@Data
public class SysConfigResponse implements Serializable {

    /** 主键（雪花 ID；库中无行时为空） */
    private Long id;

    /** 配置键（唯一，提交更新时用） */
    private String configKey;

    /** 配置名（中文展示） */
    private String configName;

    /** 配置值；是否类为 {@code "0"}/{@code "1"} */
    private String configValue;

    /** string / yes_no / int / json / model */
    private String valueType;

    /** 说明 */
    private String description;

    /** 是否类时为「是/否」；其它类型可空 */
    private String valueName;

    /**
     * valueType=model 时建议的模型类型过滤（{@link org.deepstack.ai.kernel.enums.model.ModelTypeEnum} 码）；
     * 前端调 {@code /api/models/options?modelType=} 用。
     */
    private Integer modelType;

    /** 设置页分组，前端按此分栏，一次只展示一类 */
    private String group;

    /** 当前有效值来源：{@code db} | {@code yml} | {@code default} */
    private String source;
}
