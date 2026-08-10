package com.lostfound.service;

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

    /**调用Ai接口查询物品*/
    String query(String question);
}
