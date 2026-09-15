package com.lostfound.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 大模型 API 配置。
 * <p>
 * 原来的类名是 DeepSeekConfig，绑定的配置前缀是 {@code deepseek} ——
 * 供应商名字写死在类名和配置键里，想换个模型就得改代码。
 * 现在改成通用的 {@code llm}：换供应商只改 application.yml 三项
 * （api-url / api-key / model），代码一行不动。
 * <p>
 * 能这么改是因为主流国产大模型都提供了 OpenAI 兼容的接口：
 * <pre>
 * DeepSeek     https://api.deepseek.com/v1/chat/completions
 * 硅基流动      https://api.siliconflow.cn/v1/chat/completions
 * 智谱          https://open.bigmodel.cn/api/paas/v4/chat/completions
 * </pre>
 * <p>
 * 面试可讲：为什么自定义超时 —— 默认的 RestTemplate 没有超时，
 * 下游卡住会把 Tomcat 线程池拖干，进而整个服务不可用。
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
@Data
public class LlmConfig {

    /** OpenAI 兼容的 chat completions 地址 */
    private String apiUrl;

    /** API Key */
    private String apiKey;

    /** 模型 ID */
    private String model;

    /** 连接超时（秒） */
    private int connectTimeout;

    /** 读取超时（秒） */
    private int readTimeout;

    /** 最大生成 token 数 */
    private int maxTokens;

    /** 创建配置了超时的 RestTemplate */
    @Bean
    public RestTemplate llmRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout * 1000);
        factory.setReadTimeout(readTimeout * 1000);
        return new RestTemplate(factory);
    }
}
