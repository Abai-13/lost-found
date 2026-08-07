package com.lostfound.service.impl;

import com.lostfound.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 服务 — 暂时返回占位内容，第二阶段接入大模型 API。
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Override
    public String chat(String question) {
        log.info("AI 问答请求: {}", question);
        return "AI 服务即将上线，请期待！您的问题是：" + question;
    }
}
