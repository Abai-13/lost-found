package com.lostfound.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * DeepSeek API 配置 — 绑定 application.yml 中的 deepseek 配置块，
 * 并创建带超时设置的 RestTemplate Bean。面试可讲：为什么自定义超时。
 */
@Configuration
@ConfigurationProperties(prefix = "deepseek")
@Data
public class DeepSeekConfig {

    /** DeepSeek API 地址 */
    private String apiUrl;

    /** API Key，从环境变量 DEEPSEEK_API_KEY 注入 */
    private String apiKey;

    /** 模型名称 */
    private String model;

    /** 连接超时（秒） */
    private int connectTimeout;

    /** 读取超时（秒） */
    private int readTimeout;

    /** AI 回答最大 token 数 */
    private int maxTokens;

    /** 创建配置了超时的 RestTemplate */
    @Bean
    public RestTemplate deepseekRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout * 1000);
        factory.setReadTimeout(readTimeout * 1000);
        return new RestTemplate(factory);
    }
}
