package com.lostfound.controller;

import com.lostfound.common.Result;
import com.lostfound.dto.ChatRequest;
import com.lostfound.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** AI 问答 */
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@Valid @RequestBody ChatRequest request) {
        String answer = aiService.chat(request.getQuestion());
        return Result.ok(Map.of("answer", answer));
    }
}
