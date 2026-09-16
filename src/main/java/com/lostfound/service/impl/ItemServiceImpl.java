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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;

    /**
     * 页码上限。
     * <p>
     * 压测实测：深分页（offset 上万）会让优化器放弃 idx_created_at 改走全表扫描，
     * EXPLAIN 为 {@code type=ALL, rows=125571, Extra=Using filesort}。
     * 延迟关联能治标，但更根本的是——<b>这个场景本身就不该存在</b>：
     * 失物招领不可能有人翻到第 2000 页。这里提前夹住，避免极端参数打穿数据库。
     */
    private static final int MAX_PAGE = 200;

    private void limitPageSize (ItemPageQuery query) {
        // 防止一次查询太多数据,超过50条就默认50条
        if (query.getSize()>50) {
            query.setSize(50);
        }
        // 防止深分页：页码越界直接夹到边界，不让 offset 无限变大
        if (query.getPage() < 1) {
            query.setPage(1);
        } else if (query.getPage() > MAX_PAGE) {
            query.setPage(MAX_PAGE);
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "itemPage", allEntries = true),// 新物品发布，清空所有分页缓存
            @CacheEvict(value = "itemMypage", allEntries = true),// 新物品发布，清空所有我的发布缓存)
            @CacheEvict(value = "foundPool", allEntries = true)// 候选全集变了，AI 召回必须能看到新物品
    })
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
    @Cacheable(value = "itemPage", key = "#query.page + ':' + #query.size + ':' + #query.type + ':' + #query.category + ':' + #query.keyword + ':' + #query.status + ':' + #query.upordown")
    public Page<Item> page(ItemPageQuery query) {
        limitPageSize (query);
        LambdaQueryWrapper<Item> wrapper = buildQueryWrapper(query);
        return selectPageByDeferredJoin(
                new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    @Override
    @Cacheable(value = "itemDetail", key = "#id")
    public Item getById(Long id) {
        // 查不到就返回 null，让 Spring 把 null 也缓存起来（防缓存穿透）
        return itemMapper.selectById(id);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "itemPage", allEntries = true),     // 清所有分页（数据变了）
        @CacheEvict(value = "itemDetail", key = "#itemId"),   // 清这一条详情
        @CacheEvict(value = "itemMypage", allEntries = true),    // 清所有我的发布"
        @CacheEvict(value = "foundPool", allEntries = true)      // 认领后要移出候选全集
    })
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
    @Cacheable(value = "itemMypage",key = "#userId +':'+ #query.page + ':' + #query.size + ':' + #query.type + ':' + #query.category + ':' + #query.keyword + ':' + #query.status + ':' + #query.upordown")
    public Page<Item> pageByUserId(Long userId, ItemPageQuery query) {
        limitPageSize (query);
        LambdaQueryWrapper<Item> wrapper = buildQueryWrapper(query);
        // 在公共筛选基础上，限定当前用户
        wrapper.eq(Item::getUserId, userId);
        return selectPageByDeferredJoin(
                new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    @Override
    public List<Item> selectList(ItemPageQuery query) {
        LambdaQueryWrapper<Item> wrapper = buildQueryWrapper(query);
        return itemMapper.selectList(wrapper);
    }

    /**
     * 全量未认领招领物品 —— 召回用的候选全集。
     * <p>
     * 加缓存的原因：召回每次请求都要扫一遍这个集合来打分，
     * 而集合本身变化很少（只有发布新物品或有人认领时才变）。
     * 不缓存的话每个 AI 请求都要把上千行捞出来，纯浪费。
     * <p>
     * 新鲜度靠 publish / updateStatus 上的 @CacheEvict("foundPool") 保证 ——
     * 缓存和失效必须成对写，只写一边就会出现「发布了新物品但召回永远看不到它」。
     */
    @Override
    @Cacheable(value = "foundPool", key = "'all'")
    public List<Item> listUnclaimedFound() {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Item::getType, "FOUND")
                .eq(Item::getStatus, "UNCLAIMED");
        return itemMapper.selectList(wrapper);
    }

    // ===================== 私有方法 =====================

    /**
     * 延迟关联分页 —— 解决深分页时优化器放弃索引、改走全表扫描的问题。
     * <p>
     * <b>原写法为什么慢</b>：{@code SELECT 所有列 ... ORDER BY created_at DESC LIMIT 10000,10}。
     * 外层要取所有列 → 走 idx_created_at 就必须回表，offset 一大就等于一万次随机 IO；
     * 优化器算完成本，干脆改用全表扫描 + filesort。EXPLAIN 实测：
     * {@code type=ALL, key=NULL, rows=125571, Extra=Using filesort}。
     * <p>
     * <b>拆两步为什么快</b>：① 只查 id —— idx_created_at 的叶子节点里本来就存着 id，
     * 是覆盖索引、不回表（EXPLAIN 显示 {@code Using index}），
     * 所以哪怕 {@code LIMIT 120000,10} 优化器也愿意走索引；
     * ② 只对这 size 个 id 回表取整行。
     * <p>
     * 回表次数：{@code offset + size} → {@code size}。
     */
    private Page<Item> selectPageByDeferredJoin(Page<Item> page, LambdaQueryWrapper<Item> wrapper) {
        // ① 只查 id（覆盖索引，不回表）
        wrapper.select(Item::getId);
        Page<Item> idPage = itemMapper.selectPage(page, wrapper);

        List<Item> idRecords = idPage.getRecords();
        if (idRecords.isEmpty()) {
            return idPage;
        }

        // ② 只对这几条 id 回表取完整行
        List<Long> ids = idRecords.stream().map(Item::getId).toList();
        Map<Long, Item> itemMap = itemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        // selectBatchIds 不保证返回顺序，按 id 的先后还原
        // （id 本身就是按 created_at 排好序取出来的，所以还原后顺序和原来一致）
        idPage.setRecords(ids.stream()
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .toList());
        return idPage;
    }

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
