package com.deliveryweb.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果封装：列表查询统一分页加载（对齐非功能性性能需求）。
 * 未引入 PageHelper，Dao 层手写 LIMIT 子句，offset() 即 LIMIT 起始偏移量。
 * 说明（评审 P2-5）：total 与 records 由 Service 层分两次独立 SqlSession 获取（非同一事务快照），
 * 分页计数为非强一致——有意为之的取舍（避免长事务包裹列表查询），课设规模下误差可忽略。
 */
public class Page<T> {

    /** 每页条数上限，防止恶意大页查询拖垮数据库 */
    public static final int MAX_SIZE = 50;

    /** 页码上限（P3-3 修复，2026-09-13）：page 无上限时 (page-1)*size 会溢出为负，
     *  负偏移进 LIMIT 直接 SQL 异常→500；钳制后极大页码只返回空页 */
    public static final int MAX_PAGE = 10000;

    /** 页码，从 1 开始 */
    private int page = 1;

    /** 每页条数，1-50 */
    private int size = 10;

    /** 总记录数 */
    private long total = 0;

    /** 当前页数据 */
    private List<T> records = new ArrayList<>();

    public Page() {
    }

    /**
     * 创建分页参数：page 小于 1 归 1，size 钳制在 1-50，缺省每页 10 条。
     */
    public static <T> Page<T> of(Integer page, Integer size) {
        Page<T> p = new Page<>();
        if (page != null && page > 1) {
            p.page = Math.min(page, MAX_PAGE);
        }
        if (size != null && size > 0) {
            p.size = Math.min(size, MAX_SIZE);
        }
        return p;
    }

    /** LIMIT 子句起始偏移量：(page-1)*size；二次兜底非负，防钳制前的实例被直接 set 大页码 */
    public int offset() {
        return Math.max(0, (page - 1) * size);
    }

    /** 总页数 */
    public long getPages() {
        return total == 0 ? 0 : (total + size - 1) / size;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
