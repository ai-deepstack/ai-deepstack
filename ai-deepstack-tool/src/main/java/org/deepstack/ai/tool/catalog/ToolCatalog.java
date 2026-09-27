package org.deepstack.ai.tool.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 工具持久化目录：唯一允许碰 DB 的工具元数据读取面。
 */
public interface ToolCatalog {

    /**
     * 按工具编码查询目录条目。
     *
     * @param toolCode 平台工具编码
     * @return 条目；不存在或编码空白时为空
     */
    Optional<ToolCatalogEntry> findByCode(String toolCode);

    /**
     * 按工具主键列表批量查询。
     *
     * @param toolIds 工具主键列表
     * @return 目录条目列表；空入参返回空列表
     */
    List<ToolCatalogEntry> listByIds(List<Long> toolIds);

    /**
     * 按工具编码列表批量查询（顺序与入参一致，跳过不存在项）。
     *
     * @param toolCodes 工具编码列表
     * @return 目录条目列表；空入参返回空列表
     */
    List<ToolCatalogEntry> listByCodes(List<String> toolCodes);

    /**
     * 全部启用中的目录条目（供 Index hydrate）。
     *
     * @return 启用中的目录条目列表
     */
    List<ToolCatalogEntry> listEnabled();
}
