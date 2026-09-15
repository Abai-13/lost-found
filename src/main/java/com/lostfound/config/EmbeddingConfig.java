package com.lostfound.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 向量化配置 —— 绑定 {@code embedding.*}。
 * <p>
 * 和大模型配置（{@link LlmConfig}）分开，而不是塞进同一个 {@code llm} 前缀里，
 * 原因是这两个是<b>独立的下游</b>：
 * <ul>
 *   <li>可能不是同一家供应商（大模型用 DeepSeek，向量用硅基流动是完全正常的组合）</li>
 *   <li>超时特征完全不同：对话要 30 秒读超时（模型要「想」），
 *       向量接口实测 200~300ms 返回，读超时给 5 秒都嫌多</li>
 *   <li>失败的影响面不同：对话失败 = 用户看不到答案；
 *       向量失败 = 只是这一条召回不到，接口照常返回</li>
 * </ul>
 * 配置混在一起，某天调大模型的超时就会顺手把向量的也调大，
 * 而向量超时变长意味着故障时线程被占得更久。分开配置 = 各自独立演进。
 */
@Configuration
@ConfigurationProperties(prefix = "embedding")
@Data
public class EmbeddingConfig {

    /**
     * 实现方式：{@code cloud}（云端 API）或 {@code local}（本地 ONNX，P5）。
     * <p>
     * 默认 {@code cloud} —— 保交付优先。换成 {@code local} 后
     * {@code CloudEmbeddingService} 上的 {@code @ConditionalOnProperty} 条件不成立，
     * 那个 Bean 就不会被创建。
     */
    private String provider = "cloud";

    /** OpenAI 兼容的 embeddings 地址 */
    private String apiUrl;

    /** API Key */
    private String apiKey;

    /** 模型 ID */
    private String model;

    /**
     * 期望的向量维度。
     * <p>
     * 做成配置而不是从返回值里读，是因为它得能在调用前就知道 ——
     * 存储层建表、校验 BLOB 长度都要用它。1024 是探针实测
     * {@code BAAI/bge-m3} 打出来的值，不是查文档抄的。
     */
    private int dimension;

    /**
     * 单次请求最多传多少条文本。
     * <p>
     * 64 是探针实测能一次跑通的值（258ms）。不直接设成 1000，
     * 是因为批量越大单次失败丢的越多，而且请求体太大会撞上服务端限制。
     */
    private int batchSize = 64;

    /** 连接超时（秒） */
    private int connectTimeout = 3;

    /**
     * 读取超时（秒）。
     * <p>
     * 比大模型的 30 秒短得多 —— 向量接口是「算一下」，不是「生成一段文字」，
     * 正常 200~300ms 就该回来。挂到 10 秒还没响应的，多等 20 秒也不会好。
     */
    private int readTimeout = 10;

    /** 调用失败时的重试次数（不含首次） */
    private int maxRetries = 2;

    /**
     * 向量接口专用的 RestTemplate。
     * <p>
     * ⚠️ 项目里现在有<b>两个</b> {@code RestTemplate} Bean（{@code llmRestTemplate}
     * 和这个）。Spring 按类型注入时会发现两个候选，转而<b>按字段名匹配 Bean 名</b> ——
     * 所以注入方的字段名必须叫 {@code embeddingRestTemplate}。
     * 改名会在启动时直接报 NoUniqueBeanDefinitionException，不会静默注入错的，
     * 但知道这条规则比踩到再查要省事。
     */
    @Bean
    public RestTemplate embeddingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout * 1000);
        factory.setReadTimeout(readTimeout * 1000);
        return new RestTemplate(factory);
    }
}
