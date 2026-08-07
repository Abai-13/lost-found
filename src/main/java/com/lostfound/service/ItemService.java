package com.lostfound.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.dto.ItemCreateRequest;
import com.lostfound.dto.ItemPageQuery;
import com.lostfound.entity.Item;

public interface ItemService {

    /** 发布物品 */
    Item publish(Long userId, ItemCreateRequest request);

    /** 分页查询物品列表 */
    Page<Item> page(ItemPageQuery query);

    /** 获取物品详情 */
    Item getById(Long id);

    /** 更新物品状态（如标记为已认领） */
    void updateStatus(Long itemId, Long userId, String status);
}
