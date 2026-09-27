package org.deepstack.ai.memory;

import java.util.List;

/**
 * 长期记忆存储 SPI（与知识图谱 GraphStore 分离）。
 */
public interface LongTermMemoryStore {

    /**
     * 写入一条记忆。
     *
     * @param write 写入请求（userId、content 等）
     * @return 新记忆 id
     */
    String remember(MemoryWrite write);

    /**
     * 按用户/智能体召回相关记忆。
     *
     * @param query 查询条件（userId、queryText、limit 等）
     * @return 相关记忆列表（可能为空）
     */
    List<MemoryItem> recall(MemoryQuery query);

    /**
     * 删除单条记忆。
     *
     * @param userId   用户 ID（须与记忆归属一致）
     * @param memoryId 记忆主键
     */
    void forget(String userId, String memoryId);

    /**
     * 清空某用户在指定智能体下的记忆；{@code agentCode} 为空则清空该用户全部。
     *
     * @param userId    用户 ID
     * @param agentCode 智能体编码；空则不按 agent 过滤
     */
    void clearUser(String userId, String agentCode);

    /**
     * 将召回结果格式化为可注入 prompt 的文本。
     *
     * @param items 记忆条目；null 或空返回空串
     * @return 多行文本，每行形如 {@code - [category] content}
     */
    default String formatContext(List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            MemoryItem item = items.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("- [").append(item.category()).append("] ").append(item.content());
        }
        return sb.toString();
    }
}
