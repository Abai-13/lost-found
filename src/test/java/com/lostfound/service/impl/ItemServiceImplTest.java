package com.lostfound.service.impl;

import com.lostfound.common.BusinessException;
import com.lostfound.common.ResultCode;
import com.lostfound.dto.ItemCreateRequest;
import com.lostfound.entity.Item;
import com.lostfound.mapper.ItemMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ItemServiceImpl 单元测试（纯 Mockito）。
 * 覆盖：发布（type 校验）、改状态（越权防护 + 乐观锁重试）。
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {

    @Mock
    private ItemMapper itemMapper;

    @InjectMocks
    private ItemServiceImpl itemService;

    private Item buildItem(Long id, Long userId, String status) {
        Item item = new Item();
        item.setId(id);
        item.setUserId(userId);
        item.setTitle("测试物品");
        item.setType("LOST");
        item.setStatus(status);
        item.setVersion(1);
        return item;
    }

    private ItemCreateRequest buildRequest(String type) {
        ItemCreateRequest req = new ItemCreateRequest();
        req.setTitle("校园卡");
        req.setType(type);
        req.setCategory("证件");
        req.setLocation("图书馆");
        req.setDescription("丢失校园卡一张");
        req.setContact("19029339960");
        return req;
    }

    @Test
    @DisplayName("发布：type 非法 → 抛业务异常 400，且不落库")
    void publish_invalidType_shouldThrow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.publish(1L, buildRequest("WRONG"), "img.jpg"));

        assertEquals(ResultCode.BAD_REQUEST, ex.getCode());
        verify(itemMapper, never()).insert(any(Item.class));
    }

    @Test
    @DisplayName("改状态：非发布者 → 抛 403 越权")
    void updateStatus_notOwner_shouldThrowForbidden() {
        when(itemMapper.selectById(1L)).thenReturn(buildItem(1L, 100L, "UNCLAIMED"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.updateStatus(1L, 200L, "CLAIMED"));

        assertEquals(ResultCode.FORBIDDEN, ex.getCode());
        assertEquals("只能修改自己发布的物品", ex.getMessage());
        verify(itemMapper, never()).updateById(any(Item.class));
    }

    @Test
    @DisplayName("改状态：物品不存在 → 抛 404")
    void updateStatus_itemNotFound_shouldThrow() {
        when(itemMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.updateStatus(99L, 100L, "CLAIMED"));

        assertEquals(ResultCode.NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("改状态：状态已是目标值 → 直接返回，不触发更新")
    void updateStatus_alreadyTargetStatus_shouldSkip() {
        when(itemMapper.selectById(1L)).thenReturn(buildItem(1L, 100L, "CLAIMED"));

        itemService.updateStatus(1L, 100L, "CLAIMED");

        verify(itemMapper, never()).updateById(any(Item.class));
    }

    @Test
    @DisplayName("改状态：正常更新成功")
    void updateStatus_success_shouldUpdate() {
        when(itemMapper.selectById(1L)).thenReturn(buildItem(1L, 100L, "UNCLAIMED"));
        when(itemMapper.updateById(any(Item.class))).thenReturn(1);

        itemService.updateStatus(1L, 100L, "CLAIMED");

        verify(itemMapper).updateById(any(Item.class));
    }

    @Test
    @DisplayName("改状态：乐观锁冲突一次后重试成功")
    void updateStatus_optimisticLockRetry_shouldSucceed() {
        // 每次 selectById 返回全新实例，避免上一轮 setStatus 污染下一轮
        when(itemMapper.selectById(1L)).thenAnswer(inv -> buildItem(1L, 100L, "UNCLAIMED"));
        // 第一次 updateById 返回 0（version 冲突），第二次返回 1（成功）
        when(itemMapper.updateById(any(Item.class))).thenReturn(0, 1);

        itemService.updateStatus(1L, 100L, "CLAIMED");

        verify(itemMapper, times(2)).updateById(any(Item.class));
    }

    @Test
    @DisplayName("改状态：乐观锁连续 3 次冲突 → 抛 500")
    void updateStatus_optimisticLockExhausted_shouldThrow() {
        when(itemMapper.selectById(1L)).thenAnswer(inv -> buildItem(1L, 100L, "UNCLAIMED"));
        when(itemMapper.updateById(any(Item.class))).thenReturn(0, 0, 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.updateStatus(1L, 100L, "CLAIMED"));

        assertEquals(ResultCode.INTERNAL_ERROR, ex.getCode());
        verify(itemMapper, times(3)).updateById(any(Item.class));
    }
}
