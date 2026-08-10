package com.lostfound.controller;

import com.lostfound.common.Result;
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

    /** AI根据用户询问信息查询未认领物品数据 */
    @PostMapping("/query")
    public Result<Map<String, String>> query(@Valid @RequestBody ChatRequest request,
                                            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("用户 {} 发起 AI 问答: {}", userId, request.getQuestion());
        String answer = aiService.query(request.getQuestion());
        return Result.ok(Map.of("answer", answer));
    }
}
