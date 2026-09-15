package com.lostfound.dto;

import lombok.Data;

/**
 * 单条匹配结果。
 * <p>
 * ⚠️ 注意 title / category / location 不是大模型返回的 —— 它只回 itemId 和分数。
 * 这三个字段是我们拿到 itemId 后从本地候选列表里回填的。
 * <p>
 * 为什么不让大模型一起返回：那些数据本地已经有了，让它复述一遍
 * 既浪费 token（每次多几百个），又给了它抄错的机会（幻觉）。
 * 大模型只负责它真正擅长的事 —— 判断哪条最像。
 */
@Data
public class MatchResult {

    /** 物品 id。由大模型给出，但已经在候选列表里校验过存在性，不存在会被丢弃 */
    private Long itemId;

    /** 物品标题（本地回填） */
    private String title;

    /** 类别（本地回填） */
    private String category;

    /** 丢失/拾获地点（本地回填） */
    private String location;

    /** 匹配度 0-100（大模型给出） */
    private Integer score;

    /** 匹配理由（大模型给出） */
    private String reason;
}
