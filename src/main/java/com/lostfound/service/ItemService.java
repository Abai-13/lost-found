package com.lostfound.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.dto.ItemCreateRequest;
import com.lostfound.dto.ItemPageQuery;
import com.lostfound.entity.Item;

import java.util.List;

public interface ItemService {

    /** 发布物品；imageUrl 由 Controller 层调用 FileService 获得 */
    Item publish(Long userId, ItemCreateRequest request, String imageUrl);

    /** 分页查询物品列表 */
    Page<Item> page(ItemPageQuery query);

    /** 获取物品详情 */
    Item getById(Long id);

    /** 更新物品状态（如标记为已认领） */
    void updateStatus(Long itemId, Long userId, String status);

    /** 分页查询当前用户的物品列表（个人中心用） */
    Page<Item> pageByUserId(Long userId, ItemPageQuery query);

    /** 查询所有未被认领的物品信息 */
    List<Item> selectList(ItemPageQuery query);
}
