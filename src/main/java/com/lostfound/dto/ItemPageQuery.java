package com.lostfound.dto;

import lombok.Data;

/**
 * 物品分页查询参数
 */
@Data
public class ItemPageQuery {

    /** 页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;

    /** 类型筛选: LOST / FOUND */
    private String type;

    /** 类别筛选 */
    private String category;

    /** 状态筛选 */
    private String status;

    /** 标题关键词搜索 */
    private String keyword;

    /*用户排序方式*/
    private  String upordown;
}
