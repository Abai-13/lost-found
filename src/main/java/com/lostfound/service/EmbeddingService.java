package com.lostfound.service;

import java.util.List;

/**
 * 文本向量化服务 —— 语义召回的地基（P3）。
 * <p>
 * <b>它解决什么问题</b>：2-gram 召回靠的是「两段文字有没有重合的二字片段」，
 * 所以「本子」和「笔记本」一个字都对不上，永远召不回。
 * 向量化把文本变成一串数字（坐标），意思相近的文本坐标也相近，
 * 于是「长得不像但意思一样」的东西第一次能被找到。
 * <p>
 * <b>为什么做成接口而不是直接写一个类</b>：向量化有两条完全不同的实现路线 ——
 * <ul>
 *   <li>{@code cloud} 调云端 API（当前，硅基流动 bge-m3）—— 快、准、但依赖网络和额度</li>
 *   <li>{@code local} 本地跑 ONNX 小模型（P5）—— 不花钱、断网可用、但要多引依赖</li>
 * </ul>
 * 两者的调用方代码一模一样，差别只在「向量从哪来」。
 * 接口 + {@code @ConditionalOnProperty} 切换，换实现不用改调用方一行代码 ——
 * 和 {@code llm.*} 换供应商是同一个思路。
 * <p>
 * <b>失败契约（重要）</b>：所有方法失败时返回 {@code null}，不抛异常。
 * 和 {@code AiServiceImpl.callLlm} 保持一致 —— 由调用方决定怎么降级，
 * 而不是让底层异常一路穿到 Controller 变成 500。
 */
public interface EmbeddingService {

    /**
     * 单条文本 → 向量。
     *
     * @param text 不能为空串（云端接口会直接 400）
     * @return 长度为 {@link #dimension()} 的向量；调用失败返回 {@code null}
     */
    float[] embed(String text);

    /**
     * 批量文本 → 向量，返回顺序与入参严格一致。
     * <p>
     * <b>为什么必须有批量接口</b>：实测单条调用 222ms、64 条一次调用 258ms
     * （约 4ms/条），差 55 倍。存量物品回填是几千到几万次调用，
     * 一条条调不是「慢一点」，是「慢到跑不完」。
     * <p>
     * 内部会按 {@code embedding.batch-size} 自动分批，调用方直接传全量即可。
     *
     * @param texts 任一元素为 null 或空串时整体失败返回 {@code null}（不做静默跳过，
     *              否则结果和入参对不上号，调用方无法定位是哪条有问题）
     * @return 与入参等长的向量列表；调用失败返回 {@code null}
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 向量维度（bge-m3 为 1024）。
     * <p>
     * 暴露出来是为了让存储层能校验：如果哪天供应商悄悄换了模型，
     * 维度对不上时要在写入前就报错，而不是把一堆长度不对的二进制塞进库里，
     * 等到算余弦时才在几万条数据里炸掉。
     */
    int dimension();

    /**
     * 当前实现的名字（如 {@code cloud:BAAI/bge-m3}）。
     * <p>
     * 只为可观测性存在 —— 日志和评测报告里要能看出「这批向量是谁生成的」，
     * 否则 A/B 换模型时你分不清结果差异是模型带来的还是实现带来的。
     */
    String providerName();
}
