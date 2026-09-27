package org.deepstack.ai.knowledge.graph;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 知识库图谱 scope / 节点 key 约定。
 * <p>
 * scope 与长期记忆隔离：知识库固定为 {@code kb:{baseCode}}。
 * </p>
 */
public final class KnowledgeGraphScopes {

    private KnowledgeGraphScopes() {
    }

    /** 知识库图隔离键：{@code kb:{baseCode}}。 */
    public static String scopeOf(String baseCode) {
        if (!StringUtils.hasText(baseCode)) {
            throw new IllegalArgumentException("baseCode 不能为空");
        }
        return "kb:" + baseCode.trim();
    }

    /** Chunk 节点 key：{@code chk_{chunkId}}。 */
    public static String chunkKey(Long chunkId) {
        if (chunkId == null) {
            throw new IllegalArgumentException("chunkId 不能为空");
        }
        return "chk_" + chunkId;
    }

    /** Document 节点 key：{@code doc_{documentId}}。 */
    public static String docKey(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        return "doc_" + documentId;
    }

    /**
     * Entity 节点 key：{@code ent_{normalized}}。
     * <p>
     * 归一化：trim → lower → 非字母数字下划线替换为 {@code _}，截断 64，保证首字符合法。
     * </p>
     */
    public static String entityKey(String normalizedName) {
        if (!StringUtils.hasText(normalizedName)) {
            throw new IllegalArgumentException("entity name 不能为空");
        }
        String s = normalizedName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\u4e00-\\u9fff]", "_");
        // 压缩连续下划线
        s = s.replaceAll("_+", "_");
        if (s.startsWith("_")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            s = "x";
        }
        // AGE/Cypher 标签安全：key 本身可含中文；前缀 ent_ 保证非空数字开头
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return "ent_" + s;
    }
}
