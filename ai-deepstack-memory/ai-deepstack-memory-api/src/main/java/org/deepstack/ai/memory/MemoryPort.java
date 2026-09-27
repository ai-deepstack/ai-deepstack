package org.deepstack.ai.memory;

import java.util.List;

/**
 * 运行时长期记忆端口（供图节点 / 编排注入）。
 * <p>实现由 memory-pg 等模块提供；未装配时 runtime 使用空实现。</p>
 */
public interface MemoryPort {

    /**
     * 是否已装配可用实现。
     *
     * @return true 表示可召回/写入
     */
    boolean available();

    /**
     * 召回并格式化为可注入 prompt 的文本。
     *
     * @param userId      用户 ID
     * @param agentCode   智能体编码
     * @param query       查询文本
     * @param limit       最大条数
     * @param enableGraph 是否启用图谱增强
     * @return 格式化上下文；无结果时为空串
     */
    String recallContext(String userId, String agentCode, String query, int limit, boolean enableGraph);

    /**
     * 写入一条长期记忆。
     *
     * @param userId         用户 ID
     * @param agentCode      智能体编码
     * @param content        记忆正文
     * @param category       分类（可空）
     * @param enableGraph    是否同步图谱
     * @param entities       实体列表（可空）
     * @param conversationId 会话 ID（可空）
     */
    void remember(String userId, String agentCode, String content, String category,
                  boolean enableGraph, List<String> entities, String conversationId);
}
