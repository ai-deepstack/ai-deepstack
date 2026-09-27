package org.deepstack.ai.knowledge.service;

/**
 * 知识库文档异步处理器
 * <p>
 * 单一入口 {@link #processAsync(Long)}：根据文档当前状态自动推进
 * 解析 / 切片 / 向量化流水线，幂等可重入。
 * </p>
 *
 */
public interface DocumentProcessor {

    /**
     * 异步处理文档：抓取（URL）→ 解析（FILE/URL）→ 切片 → embedding → 状态推进
     *
     * @param documentId 文档 id
     */
    void processAsync(Long documentId);
}
