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
}
