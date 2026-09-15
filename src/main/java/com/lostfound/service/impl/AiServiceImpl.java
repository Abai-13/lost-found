package com.lostfound.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lostfound.config.DeepSeekConfig;
import com.lostfound.dto.AiQueryResponse;
import com.lostfound.dto.ItemPageQuery;
import com.lostfound.dto.MatchResult;
import com.lostfound.entity.Item;
import com.lostfound.service.AiService;
import com.lostfound.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 服务实现 — 调用 DeepSeek API。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final DeepSeekConfig deepSeekConfig;
    private final RestTemplate deepseekRestTemplate;
    private final ItemService itemService;

    /** 系统提示：限制 AI 只回答失物招领相关问题 */
    private static final String SYSTEM_PROMPT =
            "你是校园失物招领助手，只回答校园失物招领、物品挂失、物品寻找相关的问题。" +
            "如果用户问不相关的问题，请礼貌地表示你只能回答失物招领相关问题。" +
            "回答尽量简洁，不超过 200 字。";

    /**
     * 匹配提示词。
     * <p>
     * 要求返回 JSON 而不是自然语言，是为了让结果可被程序消费。
     * 配套用 {@code response_format=json_object} 约束输出格式（已实测该参数 DeepSeek 支持）。
     */
    private static final String MATCH_PROMPT_TEMPLATE = """
            用户描述：%s

            候选物品列表：
            %s

            请从候选物品中挑出与用户描述最匹配的物品，最多 5 个，按匹配度从高到低排序。
            只返回 JSON，格式：
            {"answer":"给用户的一句话总结","matches":[{"itemId":数字,"score":0到100的整数,"reason":"简短理由"}]}
            要求：
            - itemId 必须是候选列表中真实存在的 id，不要编造
            - score 是 0 到 100 的整数，越大越像
            - 没有匹配的物品时 matches 返回空数组
            """;

    // ===================== 对外接口 =====================

    @Override
    public String chat(String question) {
        log.info("AI 问答请求: {}", question);
        ApiCall call = callDeepSeek(buildChatBody(question));
        if (call == null) {
            return "AI 服务繁忙，请稍后重试";
        }
        return call.content();
    }

    @Override
    public AiQueryResponse query(String question) {
        long start = System.currentTimeMillis();
        log.info("AI 物品匹配请求: {}", question);

        // ① 取候选物品（⚠️ 这里还是改造前的逻辑，见 getCandidates 的注释）
        List<Item> candidates = getCandidates();

        AiQueryResponse result = new AiQueryResponse();
        result.setCandidateCount(candidates.size());
        result.setMatches(List.of());

        // ② 拼 prompt 并调用大模型
        String prompt = String.format(MATCH_PROMPT_TEMPLATE, question, candidates.toString());
        ApiCall call = callDeepSeek(buildMatchBody(prompt));

        if (call == null) {
            result.setAnswer("AI 服务繁忙，请稍后重试");
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }

        result.setPromptTokens(call.promptTokens());
        result.setCompletionTokens(call.completionTokens());

        // ③ 解析 JSON 并回填本地字段
        try {
            JSONObject json = JSONUtil.parseObj(call.content());
            result.setAnswer(json.getStr("answer", ""));
            result.setMatches(toMatchResults(json.getJSONArray("matches"), candidates));
        } catch (Exception e) {
            // 大模型偶尔不按格式返回（最常见的是套一层 ```json 代码块围栏）。
            // 解析失败不能让整个接口挂掉 —— 把原文塞进 answer，前端至少还能显示。
            log.warn("AI 返回内容不是合法 JSON，降级为纯文本展示: {}", call.content());
            result.setAnswer(call.content());
        }

        result.setElapsedMs(System.currentTimeMillis() - start);
        log.info("AI 匹配完成: 候选 {} 条, 命中 {} 条, prompt {} tokens, 耗时 {} ms",
                result.getCandidateCount(), result.getMatches().size(),
                result.getPromptTokens(), result.getElapsedMs());
        return result;
    }

    // ===================== 候选物品 =====================

    /**
     * 取候选物品 —— 按创建时间倒序取最近 30 条未认领的招领物品。
     * <p>
     * ⚠️ 这正是本次迭代要改掉的地方，现在原样保留，作为评测基线：
     * <ul>
     *   <li>取的是「最近发布的 30 条」，不是「最像的 30 条」——
     *       两周前发布的东西压根进不了候选池，永远匹配不到</li>
     *   <li>候选数硬编码 30，物品多了之后召回被卡死（库里现在有 1001 条未认领招领物品，
     *       这个池子只覆盖 3%）</li>
     * </ul>
     */
    public List<Item> getCandidates() {
        ItemPageQuery query = new ItemPageQuery();
        query.setUpordown("DESC");
        query.setPage(1);
        query.setSize(30);
        query.setType("FOUND");
        query.setStatus("UNCLAIMED");
        return itemService.page(query).getRecords();
    }

    // ===================== 私有方法 =====================

    /** 一次 DeepSeek 调用的结果：正文 + token 用量 */
    private record ApiCall(String content, int promptTokens, int completionTokens) {
    }

    /**
     * 统一调用 DeepSeek，集中处理超时和网络异常。
     *
     * @return 调用结果；失败返回 {@code null}，由调用方决定怎么降级
     */
    private ApiCall callDeepSeek(JSONObject body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekConfig.getApiKey());
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            ResponseEntity<String> response = deepseekRestTemplate.postForEntity(
                    deepSeekConfig.getApiUrl(), entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("DeepSeek API 返回异常状态码: {}", response.getStatusCode());
                return null;
            }

            JSONObject json = JSONUtil.parseObj(response.getBody());
            String content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");

            // 把 token 用量带出来 —— 评测要拿它对比优化前后的成本，
            // 改造前这段数据是被丢掉的，导致「省了多少 token」只能靠估
            JSONObject usage = json.getJSONObject("usage");
            int promptTokens = usage == null ? 0 : usage.getInt("prompt_tokens", 0);
            int completionTokens = usage == null ? 0 : usage.getInt("completion_tokens", 0);

            return new ApiCall(content, promptTokens, completionTokens);

        } catch (RestClientException e) {
            // 连接超时、读取超时、网络异常统一在这里处理
            log.error("调用 DeepSeek API 失败", e);
            return null;
        } catch (Exception e) {
            log.error("解析 DeepSeek 返回内容失败", e);
            return null;
        }
    }

    /**
     * 把大模型返回的 matches 数组转成 {@link MatchResult}。
     * <p>
     * 两个防护：
     * <ol>
     *   <li><b>幻觉防护</b>：itemId 不在候选列表里的直接丢弃。
     *       大模型确实会编出不存在的 id，不校验就会给用户返回一个点不开的结果。</li>
     *   <li><b>本地回填</b>：title/category/location 一律用本地数据，
     *       不用大模型返回的 —— 它只负责判断「哪条最像」，回填是我们的事。</li>
     * </ol>
     */
    private List<MatchResult> toMatchResults(JSONArray arr, List<Item> candidates) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }

        Map<Long, Item> byId = new HashMap<>();
        for (Item item : candidates) {
            byId.put(item.getId(), item);
        }

        List<MatchResult> list = new ArrayList<>();
        for (Object obj : arr) {
            if (!(obj instanceof JSONObject m)) {
                continue;
            }
            Long itemId = m.getLong("itemId");
            Item item = itemId == null ? null : byId.get(itemId);
            if (item == null) {
                log.warn("大模型返回了不在候选列表中的 itemId={}，已丢弃（幻觉防护）", itemId);
                continue;
            }

            MatchResult r = new MatchResult();
            r.setItemId(item.getId());
            r.setTitle(item.getTitle());
            r.setCategory(item.getCategory());
            r.setLocation(item.getLocation());
            r.setScore(m.getInt("score", 0));
            r.setReason(m.getStr("reason", ""));
            list.add(r);
        }

        list.sort(Comparator.comparingInt(MatchResult::getScore).reversed());
        return list;
    }

    /** 构建问答请求体 */
    private JSONObject buildChatBody(String question) {
        JSONObject body = new JSONObject();
        body.set("model", deepSeekConfig.getModel());
        body.set("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", question)
        ));
        body.set("temperature", 0.7);
        body.set("max_tokens", deepSeekConfig.getMaxTokens());
        return body;
    }

    /** 构建匹配请求体。temperature=0 是因为匹配要可复现，不能每次给出不同结果 */
    private JSONObject buildMatchBody(String prompt) {
        JSONObject body = new JSONObject();
        body.set("model", deepSeekConfig.getModel());
        body.set("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        ));
        body.set("temperature", 0);
        body.set("response_format", Map.of("type", "json_object"));
        body.set("max_tokens", deepSeekConfig.getMaxTokens());
        return body;
    }
}
