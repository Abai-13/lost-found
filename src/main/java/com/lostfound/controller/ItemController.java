package com.lostfound.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.Result;
import com.lostfound.dto.ItemCreateRequest;
import com.lostfound.dto.ItemPageQuery;
import com.lostfound.entity.Item;
import com.lostfound.service.FileService;
import com.lostfound.service.ItemService;
import com.lostfound.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final UserService userService;
    private final FileService fileService;

    /**
     * 发布物品（需登录，支持图片上传）。
     * 使用 multipart/form-data 格式：
     * - data: JSON 字符串（title, type, category 等）
     * - image: 图片文件（可选）
     */
    @PostMapping
    public Result<Item> publish(@Valid @RequestPart("data") ItemCreateRequest request,
                                @RequestPart(value = "image", required = false) MultipartFile image,
                                HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        // Controller 负责处理文件上传，Service 只收 URL 字符串
        String imageUrl = fileService.upload(image);
        Item item = itemService.publish(userId, request, imageUrl);
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

    /** 我的发布列表（需登录） */
    @GetMapping("/my")
    public Result<Map<String, Object>> myItems(ItemPageQuery query,
                                               HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized();
        }
        Page<Item> page = itemService.pageByUserId(userId, query);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
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
