package org.deepstack.ai.kernel.common;

import org.deepstack.ai.kernel.model.PageInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果转换工具
 * <p>
 * MyBatis-Plus 的 {@link IPage} → 项目统一 {@link PageInfo}。
 * </p>
 *
 */
public final class PageInfoUtils {

    private PageInfoUtils() {
    }

    /**
     * 将 MP {@link IPage} 转为统一 {@link PageInfo}，记录通过 mapper 函数转换为目标类型。
     *
     * @param page   MP 分页对象
     * @param mapper entity → vo 的转换函数
     * @param <E>    entity 类型
     * @param <V>    vo 类型
     * @return PageInfo
     */
    public static <E, V> PageInfo<V> of(IPage<E> page, Function<E, V> mapper) {
        List<V> rows = page.getRecords().stream().map(mapper).toList();
        long total = page.getTotal();
        long size = page.getSize();
        long totalPage = size == 0 ? 0 : (total + size - 1) / size;
        return new PageInfo<>((int) page.getCurrent(), (int) size, totalPage, total, rows);
    }
}
