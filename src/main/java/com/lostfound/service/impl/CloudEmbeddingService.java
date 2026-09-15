package com.lostfound.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lostfound.common.RetrySupport;
import com.lostfound.config.EmbeddingConfig;
import com.lostfound.service.EmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 云端向量化实现 —— 调用 OpenAI 兼容的 {@code /v1/embeddings} 接口。
 * <p>
 * 当前指向硅基流动的 {@code BAAI/bge-m3}（免费档，1024 维）。
 * <p>
 * <b>接口能力是探针实测出来的，不是查文档抄的</b>（{@code eval/probe-embedding.js}）：
 * <pre>
 *   单条 222ms，一次 64 条 258ms（约 4ms/条）—— 批量比单条快 55 倍
 *   返回结构 {object, data:[{embedding, index, object}], model, usage}
 *   维度 1024，usage 里有 prompt_tokens（按 token 计费/限流）
 * </pre>
 * <p>
 * <b>为什么用 RestTemplate 而不是 WebClient</b>：项目是同步的 Spring MVC，
 * 引入 WebClient 会把响应式那一套（Mono/Flux、事件循环线程）一起拖进来，
 * 为一个接口不值当。真正需要异步的是「发布物品时不该等向量」这件事，
 * 那个用 {@code @Async} 丢给线程池就够了（P3.2），不需要换整个编程模型。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "embedding", name = "provider",
        havingValue = "cloud", matchIfMissing = true)
@RequiredArgsConstructor
public class CloudEmbeddingService implements EmbeddingService {

    private final EmbeddingConfig embeddingConfig;
    private final RestTemplate embeddingRestTemplate;

    /**
     * 启动时自检 —— 配错了要现在就知道，不能等到第一次调用。
     * <p>
     * 空 key 会让每次请求都 401，而失败契约是「返回 null 不抛异常」，
     * 于是整个向量化静默失效，你只会在几天后看到「召回率没涨」时才发现。
     * 这正是 Redis 序列化那个 bug 的同一个形状：<b>失败被降级吞掉</b>。
     */
    @PostConstruct
    void checkConfig() {
        if (embeddingConfig.getApiKey() == null || embeddingConfig.getApiKey().isBlank()) {
            log.warn("embedding.api-key 未配置，云端向量化将全程失败（返回 null）。"
                    + "本地开发请在 application-local.yml 的 embedding 段填上 key。");
        } else {
            log.info("向量化服务就绪: provider={}, model={}, dimension={}, batchSize={}",
                    embeddingConfig.getProvider(), embeddingConfig.getModel(),
                    embeddingConfig.getDimension(), embeddingConfig.getBatchSize());
        }
    }

    @Override
    public float[] embed(String text) {
        List<float[]> result = embedBatch(List.of(text));
        return result == null ? null : result.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        // 空文本直接判失败而不是跳过：跳过会让返回值和入参下标错位，
        // 调用方拿到一条长度不对的结果，却不知道是哪条坏的 —— 这种 bug 极难查。
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t == null || t.isBlank()) {
                log.error("向量化入参第 {} 条为空，整批放弃（共 {} 条）", i, texts.size());
                return null;
            }
        }

        int batchSize = Math.max(1, embeddingConfig.getBatchSize());
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> chunk = texts.subList(from, Math.min(from + batchSize, texts.size()));
            List<float[]> part = callOnce(chunk, 0);
            if (part == null) {
                log.error("向量化失败，已完成 {}/{} 条，本批及后续放弃", from, texts.size());
                return null;
            }
            vectors.addAll(part);
        }
        return vectors;
    }

    @Override
    public int dimension() {
        return embeddingConfig.getDimension();
    }

    @Override
    public String providerName() {
        return "cloud:" + embeddingConfig.getModel();
    }

    // ===================== 私有方法 =====================

    /**
     * 发一次请求，失败按可重试性决定是否退避重试。
     *
     * @return 向量列表；重试后仍失败返回 {@code null}
     */
    private List<float[]> callOnce(List<String> texts, int attempt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(embeddingConfig.getApiKey());

        JSONObject body = new JSONObject();
        body.set("model", embeddingConfig.getModel());
        body.set("input", texts);
        // 明确要 float，不要服务端默认的可能被量化过的格式（如 base64）
        body.set("encoding_format", "float");

        try {
            ResponseEntity<String> response = embeddingRestTemplate.postForEntity(
                    embeddingConfig.getApiUrl(), new HttpEntity<>(body.toString(), headers), String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("向量接口返回异常状态码: {}", response.getStatusCode());
                return null;
            }
            return parse(response.getBody(), texts.size());

        } catch (RestClientException e) {
            if (attempt < embeddingConfig.getMaxRetries() && RetrySupport.isRetryable(e)) {
                long waitMs = RetrySupport.backoffMs(attempt);
                log.warn("向量接口调用失败（第 {} 次尝试），{}ms 后重试: {}",
                        attempt + 1, waitMs, e.getMessage());
                RetrySupport.sleepQuietly(waitMs);
                return callOnce(texts, attempt + 1);
            }
            log.error("调用向量接口失败（共尝试 {} 次）", attempt + 1, e);
            return null;
        } catch (Exception e) {
            log.error("解析向量接口返回内容失败", e);
            return null;
        }
    }

    /**
     * 解析返回体。
     * <p>
     * 两个必须做的校验：
     * <ol>
     *   <li><b>按 index 排序</b>。OpenAI 兼容接口在 data 里带 {@code index} 字段，
     *       规范上不保证返回顺序等于请求顺序。不排序的话，批量回填会把 A 物品的向量
     *       写到 B 物品头上 —— 而且不会报任何错，只是匹配结果莫名其妙地烂
     *       （这类「静默错位」是最难查的一类 bug）</li>
     *   <li><b>校验维度</b>。维度对不上说明供应商换了模型或加了截断，
     *       这种数据存进库里，要等到算余弦时才炸，那时已经查不出是哪一批写坏的</li>
     * </ol>
     */
    private List<float[]> parse(String responseBody, int expectedCount) {
        JSONObject json = JSONUtil.parseObj(responseBody);
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.size() != expectedCount) {
            log.error("向量接口返回条数不对: 期望 {}，实际 {}",
                    expectedCount, data == null ? 0 : data.size());
            return null;
        }

        // 先按 index 归位，再转成 float[]
        JSONObject[] byIndex = new JSONObject[expectedCount];
        for (Object obj : data) {
            if (!(obj instanceof JSONObject item)) {
                continue;
            }
            Integer index = item.getInt("index");
            if (index == null || index < 0 || index >= expectedCount) {
                log.error("向量接口返回了越界的 index={}", index);
                return null;
            }
            byIndex[index] = item;
        }

        int expectedDim = embeddingConfig.getDimension();
        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            JSONObject item = byIndex[i];
            if (item == null) {
                log.error("向量接口返回缺失 index={}", i);
                return null;
            }
            JSONArray arr = item.getJSONArray("embedding");
            if (arr == null || arr.size() != expectedDim) {
                log.error("向量维度不符: 期望 {}，实际 {}（供应商可能换了模型，"
                        + "请用 eval/probe-embedding.js 重新确认后改 embedding.dimension）",
                        expectedDim, arr == null ? 0 : arr.size());
                return null;
            }
            float[] vector = new float[expectedDim];
            for (int d = 0; d < expectedDim; d++) {
                vector[d] = arr.getDouble(d).floatValue();
            }
            vectors.add(vector);
        }

        JSONObject usage = json.getJSONObject("usage");
        if (usage != null) {
            // 记 token 用量是为了对账：免费档有 TPM 限制，回填大批量时
            // 要靠这个数判断「是不是快到限流线了」，而不是等 429 打脸
            log.debug("向量化 {} 条，消耗 {} tokens", expectedCount, usage.getInt("total_tokens", 0));
        }
        return vectors;
    }
}
