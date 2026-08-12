package com.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.BusinessException;
import com.lostfound.common.ResultCode;
import com.lostfound.dto.ItemCreateRequest;
import com.lostfound.dto.ItemPageQuery;
import com.lostfound.entity.Item;
import com.lostfound.mapper.ItemMapper;
import com.lostfound.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;

    @Override
    public Item publish(Long userId, ItemCreateRequest request, String imageUrl) {
        // 校验 type 取值
        if (!"LOST".equals(request.getType()) && !"FOUND".equals(request.getType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "物品类型只能为 LOST 或 FOUND");
        }

        Item item = new Item();
        item.setUserId(userId);
        item.setTitle(request.getTitle());
        item.setType(request.getType());
        item.setCategory(request.getCategory());
        item.setLocation(request.getLocation());
        item.setDescription(request.getDescription());
        item.setImageUrl(imageUrl);
        item.setContact(request.getContact());
        item.setStatus("UNCLAIMED");

        itemMapper.insert(item);
        log.info("新物品发布: id={}, title={}, type={}", item.getId(), item.getTitle(), item.getType());
        return item;
    }

    @Override
    public Page<Item> page(ItemPageQuery query) {
        LambdaQueryWrapper<Item> wrapper = buildQueryWrapper(query);
        return itemMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    @Override
    public Item getById(Long id) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "物品不存在");
        }
        return item;
    }

    @Override
    public void updateStatus(Long itemId, Long userId, String status) {
        // 乐观锁 + 重试：最多重试 3 次，防止并发认领冲突
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            Item item = itemMapper.selectById(itemId);
            if (item == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "物品不存在");
            }
            // 只有发布者本人才能修改状态
            if (!item.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己发布的物品");
            }
            // 如果状态已经是目标状态，则无需修改，直接返回
            if(item.getStatus().equals(status)) {
                return;
            }
            item.setStatus(status);
            int rows = itemMapper.updateById(item);
            if (rows > 0) {
                // 更新成功 — MyBatis-Plus 自动将 version + 1
                log.info("物品状态更新: id={}, status={}, version={}", itemId, status, item.getVersion());
                return;
            }
            // rows = 0 说明 version 已变化 → 被别人抢先了，重试
            log.warn("乐观锁冲突，第 {} 次重试: id={}", i + 1, itemId);
        }
        // 3 次都冲突 → 告诉用户重试
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "操作冲突，请稍后重试");
    }

    @Override
    public Page<Item> pageByUserId(Long userId, ItemPageQuery query) {
        LambdaQueryWrapper<Item> wrapper = buildQueryWrapper(query);
        // 在公共筛选基础上，限定当前用户
        wrapper.eq(Item::getUserId, userId);
        return itemMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    @Override
    public List<Item> selectList(ItemPageQuery query) {
        LambdaQueryWrapper<Item> wrapper = buildQueryWrapper(query);
        return itemMapper.selectList(wrapper);
    }

    // ===================== 私有方法 =====================

    /** 构建公共查询条件（type/category/status/keyword/排序） */
    private LambdaQueryWrapper<Item> buildQueryWrapper(ItemPageQuery query) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getType())) {
            wrapper.eq(Item::getType, query.getType());
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(Item::getCategory, query.getCategory());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Item::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Item::getTitle, query.getKeyword());
        }
        if ("ASC".equals(query.getUpordown())) {
            wrapper.orderByAsc(Item::getCreatedAt);
        } else {
            wrapper.orderByDesc(Item::getCreatedAt);
        }

        return wrapper;
    }
}
