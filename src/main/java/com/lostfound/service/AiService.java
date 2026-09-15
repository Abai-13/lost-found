package com.lostfound.service;

import com.lostfound.dto.AiQueryResponse;

/**
 * AI 服务接口 — 第二阶段实现。
 */
public interface AiService {

    /**
     * AI 问答 — 用户用自然语言提问，返回 AI 生成的回答。
     * @param question 用户问题
     * @return AI 回答文本
     */
    String chat(String question);

    /**
     * 根据用户的自然语言描述匹配失物/招领物品。
     * <p>
     * 返回结构化结果（含匹配度和理由），而不是一段自然语言 ——
     * 这样前端能把结果渲染成可点击的卡片，评测脚本也能算出 Recall@K。
     *
     * @param question 用户对丢失物品的描述
     * @return 匹配结果 + 本次调用的 token / 耗时统计
     */
    AiQueryResponse query(String question);
}
