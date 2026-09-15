package com.lostfound.controller;

import com.lostfound.common.Result;
import com.lostfound.dto.AiQueryResponse;
import com.lostfound.dto.ChatRequest;
import com.lostfound.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 问答控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** AI 问答（需登录） */
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@Valid @RequestBody ChatRequest request,
                                             HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("用户 {} 发起 AI 问答: {}", userId, request.getQuestion());
        String answer = aiService.chat(request.getQuestion());
        return Result.ok(Map.of("answer", answer));
    }

    /**
     * AI 物品匹配：根据用户的自然语言描述，从候选物品里挑出最像的几条。
     * <p>
     * 返回结构化结果（matches 数组），前端可以渲染成可点击的物品卡片。
     */
    @PostMapping("/query")
    public Result<AiQueryResponse> query(@Valid @RequestBody ChatRequest request,
                                            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("用户 {} 发起 AI 物品匹配: {}", userId, request.getQuestion());
        return Result.ok(aiService.query(request.getQuestion()));
    }
}
