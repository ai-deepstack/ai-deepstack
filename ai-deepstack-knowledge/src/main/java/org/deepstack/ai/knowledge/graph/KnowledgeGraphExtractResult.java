package org.deepstack.ai.knowledge.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 实体/关系抽取结果（单批或多批合并后）。
 */
public final class KnowledgeGraphExtractResult {

    private final List<Entity> entities;
    private final List<Relation> relations;

    public KnowledgeGraphExtractResult(List<Entity> entities, List<Relation> relations) {
        this.entities = entities == null ? List.of() : List.copyOf(entities);
        this.relations = relations == null ? List.of() : List.copyOf(relations);
    }

    /** 返回空结果占位。 */
    public static KnowledgeGraphExtractResult empty() {
        return new KnowledgeGraphExtractResult(List.of(), List.of());
    }

    /** 返回 Entities。 */
    public List<Entity> getEntities() {
        return entities;
    }

    /** 返回 Relations。 */
    public List<Relation> getRelations() {
        return relations;
    }

    /** 合并多批结果（同名实体后写覆盖 type）。 */
    public static KnowledgeGraphExtractResult merge(List<KnowledgeGraphExtractResult> parts) {
        if (parts == null || parts.isEmpty()) {
            return empty();
        }
        java.util.LinkedHashMap<String, Entity> entMap = new java.util.LinkedHashMap<>();
        List<Relation> rels = new ArrayList<>();
        for (KnowledgeGraphExtractResult p : parts) {
            if (p == null) {
                continue;
            }
            for (Entity e : p.getEntities()) {
                if (e != null && e.name() != null && !e.name().isBlank()) {
                    entMap.put(e.name().trim().toLowerCase(java.util.Locale.ROOT), e);
                }
            }
            rels.addAll(p.getRelations());
        }
        return new KnowledgeGraphExtractResult(new ArrayList<>(entMap.values()), rels);
    }

    /** Entity。 */
    public record Entity(String name, String type) {
    }

    /** Relation。 */
    public record Relation(String from, String to, String type) {
    }
}
