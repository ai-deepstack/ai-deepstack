package org.deepstack.ai.agent.model.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 可用节点类型信息（供前端节点面板渲染）
 *
 */
@Data
public class NodeTypeResponse implements Serializable {

    /**
     * 节点类型编码
     */
    private String type;

    /**
     * 显示名称
     */
    private String label;

    /**
     * 图标（Element UI icon class）
     */
    private String icon;

    /**
     * 分类：basic / ai / tool / flow
     */
    private String category;

    /**
     * 默认属性（前端创建节点时合并）
     */
    private Map<String, Object> defaultProperties;

    /**
     * 属性配置描述（前端渲染属性面板）
     */
    private List<PropertyField> propertyFields;

    /**
     * 最大入口数（-1 = 无限制）
     */
    private int maxInPorts = -1;

    /**
     * 最大出口数（-1 = 无限制，1 = 单出口，>1 = 有限出口）
     */
    private int maxOutPorts = -1;

    @Data
    public static class PropertyField implements Serializable {
        /**
         * 属性 key
         */
        private String key;

        /**
         * 显示名称
         */
        private String label;

        /**
         * 控件类型：input / textarea / select / number / switch / json / variable
         */
        private String widget;

        /**
         * 是否必填
         */
        private boolean required;

        /**
         * 默认值
         */
        private Object defaultValue;

        /**
         * 选项列表（widget=select 时有值）
         */
        private List<Map<String, Object>> options;

        /**
         * 提示文本
         */
        private String placeholder;
    }
}
