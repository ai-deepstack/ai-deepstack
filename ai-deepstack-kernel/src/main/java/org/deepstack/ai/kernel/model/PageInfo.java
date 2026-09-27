package org.deepstack.ai.kernel.model;

import java.io.Serializable;
import java.util.List;

public class PageInfo<T> implements Serializable {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private Long totalPage;
    private long total;
    private List<T> list;

    public PageInfo() {
    }

    public PageInfo(Integer page, Integer pageSize) {
        this.pageNum = page;
        this.pageSize = pageSize;
    }

    public PageInfo(Integer page, Integer pageSize, Long totalPage, long total) {
        this.pageNum = page;
        this.pageSize = pageSize;
        this.totalPage = totalPage;
        this.total = total;
    }

    public PageInfo(Integer page, Integer pageSize, Long totalPage, long total, List<T> list) {
        this.pageNum = page;
        this.pageSize = pageSize;
        this.totalPage = totalPage;
        this.total = total;
        this.list = list;
    }

    /** 返回 PageNum。 */
    public Integer getPageNum() {
        return pageNum;
    }

    /** 设置 PageNum。 */
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    /** 返回 PageSize。 */
    public Integer getPageSize() {
        return pageSize;
    }

    /** 设置 PageSize。 */
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    /** 返回 TotalPage。 */
    public Long getTotalPage() {
        return totalPage;
    }

    /** 设置 TotalPage。 */
    public void setTotalPage(Long totalPage) {
        this.totalPage = totalPage;
    }

    /** 返回 Total。 */
    public long getTotal() {
        return total;
    }

    /** 设置 Total。 */
    public void setTotal(long total) {
        this.total = total;
    }

    /** 返回 List。 */
    public List<T> getList() {
        return list;
    }

    /** 设置 List。 */
    public void setList(List<T> list) {
        this.list = list;
    }
}
