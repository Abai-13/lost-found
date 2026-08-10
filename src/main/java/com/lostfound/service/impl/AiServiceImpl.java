package com.lostfound.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lostfound.config.DeepSeekConfig;
import com.lostfound.dto.ItemPageQuery;
import com.lostfound.entity.Item;
import com.lostfound.service.AiService;
import com.lostfound.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AI 服务实现 — 调用 DeepSeek API 进行真实问答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final DeepSeekConfig deepSeekConfig;
    private final RestTemplate deepseekRestTemplate;

    /** 系统提示：限制 AI 只回答失物招领相关问题 */
    private static final String SYSTEM_PROMPT =
            "你是校园失物招领助手，只回答校园失物招领、物品挂失、物品寻找相关的问题。" +
            "如果用户问不相关的问题，请礼貌地表示你只能回答失物招领相关问题。" +
            "回答尽量简洁，不超过 200 字。";
    private final ItemService itemService;

    @Override
    public String chat(String question) {
        log.info("AI 问答请求: {}", question);

        // ① 构建请求体 JSON
        JSONObject requestBody = buildRequestBody(question);

        // ② 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekConfig.getApiKey());

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);

        // ③ 发送请求 + 异常降级
        try {
            ResponseEntity<String> response = deepseekRestTemplate.postForEntity(
                    deepSeekConfig.getApiUrl(),
                    requestEntity,
                    String.class
            );

            // ④ 检查 HTTP 状态码
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("DeepSeek API 返回异常状态码: {}", response.getStatusCode());
                return "AI 服务异常，请联系管理员";
            }

            // ⑤ 解析返回 JSON，提取 answer
            return extractAnswer(response.getBody());

        } catch (RestClientException e) {
            // 连接超时、读取超时、网络异常统一在这里处理
            log.error("调用 DeepSeek API 失败", e);
            return "AI 服务繁忙，请稍后重试";
        }
    }

    // 查出未认领招领物品
    public List<Item> getCandidates() {
        ItemPageQuery query = new ItemPageQuery();//创建查询对象
        query.setUpordown("DESC");
        query.setPage(1);
        query.setSize(30);
        query.setType("FOUND");//设置查询类型为未认领物品qian
        query.setStatus("UNCLAIMED");//设置查询状态为未认领
        List<Item> candidates=itemService.page(query).getRecords();// 查询物品
        return candidates;
    }

    /** 使用Ai查询物品信息并返回物品和匹配度 */
    @Override
    public String query(String question) {
        log.info("AI 问答请求: {}", question);
        String prompt= "用户描述：" +question
                + "\n候选物品列表：\n" + getCandidates().toString();

        // ① 构建请求体 JSON
        JSONObject requestBody = buildRequestBody(prompt);

        // ② 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekConfig.getApiKey());

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);
        // ③ 发送请求 + 异常降级
        try {
            ResponseEntity<String> response = deepseekRestTemplate.postForEntity(
                    deepSeekConfig.getApiUrl(),
                    requestEntity,
                    String.class
            );

            // ④ 检查 HTTP 状态码
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("DeepSeek API 返回异常状态码: {}", response.getStatusCode());
                return "AI 服务异常，请联系管理员";
            }

            // ⑤ 解析返回 JSON，提取 answer
            return extractAnswer(response.getBody());

        } catch (RestClientException e) {
            // 连接超时、读取超时、网络异常统一在这里处理
            log.error("调用 DeepSeek API 失败", e);
            return "AI 服务繁忙，请稍后重试";
        }
    }

    /** 构建 DeepSeek API 请求体 */
    private JSONObject buildRequestBody(String question) {
        JSONObject body = new JSONObject();
        // ① 构建请求体 JSON
        body.set("model", deepSeekConfig.getModel());
        body.set("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", question)
        ));
        body.set("temperature", 0.7);
        body.set("max_tokens", deepSeekConfig.getMaxTokens());
        return body;
    }

    /** 从 DeepSeek 返回的 JSON 中提取回答内容 */
    private String extractAnswer(String responseBody) {
        try {
            JSONObject json = JSONUtil.parseObj(responseBody);
            // 路径: choices[0].message.content
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");
        } catch (Exception e) {
            log.error("解析 DeepSeek 返回 JSON 失败: {}", responseBody, e);
            return "AI 返回数据解析失败，请稍后重试";
        }
    }



}
