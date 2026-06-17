package com.gechuang.stationery.common;

import java.util.List;

public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final int pageNo;
    private final int pageSize;
    private final long pages;

    public PageResult(List<T> records, long total, int pageNo, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.pages = pageSize <= 0 ? 0 : (long) Math.ceil((double) total / pageSize);
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPageNo() {
        return pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getPages() {
        return pages;
    }
}
