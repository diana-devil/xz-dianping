package com.xzdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页结果封装
 */
@Data
public class ScrollResult {
    /**
     * 查询数据
     */
    private List<?> list;

    /**
     * 本次查询的最小时间戳
     */
    private Long minTime;

    /**
     * 下次查询的偏移量
     * 即，本次查询中，相同的最小时间戳的个数
     */
    private Integer offset;
}
