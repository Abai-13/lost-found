package com.lostfound.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.Result;
import com.lostfound.dto.ItemCreateRequest;
import com.lostfound.dto.ItemPageQuery;
import com.lostfound.entity.Item;
import com.lostfound.service.ItemService;
import com.lostfound.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final UserService userService;

    /** 发布物品（需登录） */
    @PostMapping
    public Result<Item> publish(@Valid @RequestBody ItemCreateRequest request,
                                HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Item item = itemService.publish(userId, request);
        return Result.ok("发布成功", item);
    }

    /** 物品列表（分页 + 筛选） */
    @GetMapping
    public Result<Map<String, Object>> list(ItemPageQuery query) {
        Page<Item> page = itemService.page(query);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
        return Result.ok(result);
    }

    /** 物品详情 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Item item = itemService.getById(id);
        String nickname = userService.getNicknameById(item.getUserId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item", item);
        result.put("publisherName", nickname);
        return Result.ok(result);
    }

    /** 修改物品状态（需登录，仅发布者本人） */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(@PathVariable Long id,
                                     @RequestParam String status,
                                     HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        itemService.updateStatus(id, userId, status);
        return Result.ok("更新成功");
    }
}
