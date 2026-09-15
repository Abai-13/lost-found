package com.lostfound.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 物品匹配的返回体。
 * <p>
 * 改造前这个接口返回的是一段自然语言文本（{@code {"answer": "我帮你找到了..."}}），
 * 前端只能整段渲染。三个后果：
 * <ol>
 *   <li>没法做成物品卡片 —— 结果里没有 id，点不进详情页</li>
 *   <li>大模型每次输出的措辞和格式都不一样，前端没法稳定解析</li>
 *   <li><b>测不了</b> —— 结果是段文字，算不出 Recall@K，优化前后没得比</li>
 * </ol>
 * 结构化之后，matches 给前端渲染，token / 耗时字段给评测脚本统计成本。
 */
@Data
public class AiQueryResponse {

    /** 给用户看的一句话总结 */
    private String answer;

    /** 匹配到的物品，按匹配度从高到低 */
    private List<MatchResult> matches;

    /** 这次喂给大模型多少条候选 —— 用来诊断召回是不是被候选池卡住了 */
    private int candidateCount;

    /**
     * 这次实际召回的候选 id 列表（按召回得分从高到低）。
     * <p>
     * 用途是**把失败归因到具体的层**。只看最终命中率的话，
     * 「没召回到」和「召回到了但大模型没挑中」长得一模一样，
     * 而这两种情况要修的地方完全不同（前者改召回，后者改精排）。
     * 有了这个字段，评测脚本就能分开算两层的成功率。
     */
    private List<Long> candidateIds;

    /** 本次消耗的 prompt token。评测要拿它对比优化前后的成本 */
    private int promptTokens;

    /** 本次消耗的 completion token */
    private int completionTokens;

    /** 端到端耗时（毫秒），含查库 + 调大模型 */
    private long elapsedMs;
}
