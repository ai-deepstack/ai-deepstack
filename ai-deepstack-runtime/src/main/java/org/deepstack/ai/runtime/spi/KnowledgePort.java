package org.deepstack.ai.runtime.spi;

import java.util.List;

/**
 * 知识检索端口：runtime 不依赖 knowledge 模块实体/服务。
 * <p>
 * 单库 / 多库统一由 knowledge 侧 Facade 实现；多库并行与限流对调用方透明。
 * </p>
 */
public interface KnowledgePort {

    /**
     * 按知识库编码检索，返回可直接拼进 prompt 的文本片段。
     *
     * @param baseCode            知识库编码
     * @param query               查询文本
     * @param topK                召回条数（库级 topK 优先时由实现决定）
     * @param similarityThreshold 相似度阈值
     * @return 文本内容列表；知识库不存在或无命中时为空列表
     */
    List<String> retrieve(String baseCode, String query, int topK, double similarityThreshold);

    /**
     * 多库并行召回，按 {@code baseCodes} 输入顺序拼接文本块。
     * <p>
     * 单库超时/失败不影响其他库；实现侧使用 Semaphore 限流。
     * 默认实现：逐库串行调用 {@link #retrieve}（缺省 Bean / 旧适配器用）。
     * </p>
     *
     * @param baseCodes           有序知识库编码列表
     * @param query               查询文本
     * @param topK                默认召回条数
     * @param similarityThreshold 默认相似度阈值
     * @return 按库顺序扁平化的文本列表
     */
    default List<String> retrieveAll(List<String> baseCodes, String query,
                                     int topK, double similarityThreshold) {
        if (baseCodes == null || baseCodes.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<String> all = new java.util.ArrayList<>();
        for (String code : baseCodes) {
            List<String> part = retrieve(code, query, topK, similarityThreshold);
            if (part != null && !part.isEmpty()) {
                all.addAll(part);
            }
        }
        return all;
    }
}
