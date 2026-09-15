package com.lostfound.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lostfound.config.LlmConfig;
import com.lostfound.dto.AiQueryResponse;
import com.lostfound.dto.MatchResult;
import com.lostfound.entity.Item;
import com.lostfound.service.AiService;
import com.lostfound.service.CandidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * AI 服务实现 — 调用大模型完成问答和物品匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final LlmConfig llmConfig;
    private final RestTemplate llmRestTemplate;
    private final CandidateService candidateService;

    /**
     * 召回后送进大模型的候选条数。
     * <p>
     * 做成配置项的理由和限流阈值一样：这是运维参数，要按实测调。
     * 调小了召回不够（池子里的东西进不来），调大了 token 上去、干扰项也变多。
     */
    @Value("${ai.recall.top-k:50}")
    private int recallTopK;

    /** 系统提示：限制 AI 只回答失物招领相关问题 */
    private static final String SYSTEM_PROMPT =
            "你是校园失物招领助手，只回答校园失物招领、物品挂失、物品寻找相关的问题。" +
            "如果用户问不相关的问题，请礼貌地表示你只能回答失物招领相关问题。" +
            "回答尽量简洁，不超过 200 字。";

    /**
     * 匹配提示词。
     * <p>
     * 要求返回 JSON 而不是自然语言，是为了让结果可被程序消费。
     * 配套用 {@code response_format=json_object} 约束输出格式（已实测该参数 DeepSeek 支持，硅基流动等 OpenAI 兼容接口同样支持）。
     */
    private static final String MATCH_PROMPT_TEMPLATE = """
            用户描述：%s

            %s

            请从候选物品中挑出与用户描述最匹配的物品，最多 5 个，按匹配度从高到低排序。
            只返回 JSON，格式：
            {"answer":"给用户的一句话总结","matches":[{"itemId":数字,"score":0到100的整数,"reason":"简短理由"}]}
            要求：
            - itemId 必须是候选列表中真实存在的 id，不要编造
            - score 是 0 到 100 的整数，越大越像
            - 没有匹配的物品时 matches 返回空数组
            """;

    /** 描述字段在 prompt 里的最大长度，超出截断 */
    private static final int DESC_MAX_LEN = 60;

    // ===================== 对外接口 =====================

    @Override
    public String chat(String question) {
        log.info("AI 问答请求: {}", question);
        ApiCall call = callLlm(buildChatBody(question));
        if (call == null) {
            return "AI 服务繁忙，请稍后重试";
        }
        return call.content();
    }

    @Override
    public AiQueryResponse query(String question) {
        long start = System.currentTimeMillis();
        log.info("AI 物品匹配请求: {}", question);

        // ① 召回候选物品 —— 从「最近发布的 30 条」改成「全量 + 2-gram 打分取 Top-K」
        List<Item> candidates = candidateService.recall(question, recallTopK);

        AiQueryResponse result = new AiQueryResponse();
        result.setCandidateCount(candidates.size());
        result.setCandidateIds(candidates.stream().map(Item::getId).toList());
        result.setMatches(List.of());

        // ② 一个候选都没有时，别调大模型 —— 白花一次 token 和 1 秒延迟。
        //    模型面对空列表只有两种反应：说「没找到」，或者开始编。
        //    这两种结果我们本来就知道，没必要花钱问一次。
        if (candidates.isEmpty()) {
            result.setAnswer("没有找到相关的招领信息。可以换个说法再试试，"
                    + "或者直接去失物招领处登记一下。");
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }

        // ③ 拼 prompt 并调用大模型
        String prompt = String.format(MATCH_PROMPT_TEMPLATE, question, formatCandidates(candidates));
        ApiCall call = callLlm(buildMatchBody(prompt));

        if (call == null) {
            result.setAnswer("AI 服务繁忙，请稍后重试");
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }

        result.setPromptTokens(call.promptTokens());
        result.setCompletionTokens(call.completionTokens());

        // ④ 解析 JSON 并回填本地字段
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

    // ===================== 私有方法 =====================
    //
    // 候选物品的取法已移交 CandidateService:
    //   改造前：itemService.page(最近 30 条) —— 取的是「最近」不是「最像」，池外命中率 0%
    //   改造后：CandidateService.recall()  —— 全量候选 + 2-gram 打分取 Top-K
    // 保留这条注释是因为「为什么换掉」比「换成了什么」更值得记住。

    /** 一次大模型调用的结果：正文 + token 用量 */
    private record ApiCall(String content, int promptTokens, int completionTokens) {
    }

    /**
     * 统一调用大模型，集中处理超时和网络异常。
     * <p>
     * 具体调哪家由 application.yml 的 llm.* 决定 —— 都是 OpenAI 兼容接口。
     *
     * @return 调用结果；失败返回 {@code null}，由调用方决定怎么降级
     */
    private ApiCall callLlm(JSONObject body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmConfig.getApiKey());
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            ResponseEntity<String> response = llmRestTemplate.postForEntity(
                    llmConfig.getApiUrl(), entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("大模型 API 返回异常状态码: {}", response.getStatusCode());
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
            log.error("调用大模型 API 失败", e);
            return null;
        } catch (Exception e) {
            log.error("解析大模型返回内容失败", e);
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

    /**
     * 把候选物品格式化成给大模型看的紧凑文本。
     * <p>
     * 改造前直接用的是 {@code List<Item>.toString()}（Lombok 的 @Data 生成的），
     * 输出长这样：
     * <pre>
     * Item(id=1000, userId=2, title=黑色iPhone 15, type=FOUND, category=电子产品,
     *      location=图书馆三楼, description=..., imageUrl=null, contact=null,
     *      status=UNCLAIMED, version=0, createdAt=2026-09-15T01:44:48,
     *      updatedAt=2026-09-15T01:44:48)
     * </pre>
     * <p>
     * 问题有两个：
     * <ol>
     *   <li><b>大部分字段是噪音</b>。模型要判断「这像不像我丢的东西」，只需要
     *       标题 / 类别 / 地点 / 描述。userId、version、imageUrl、contact、status、
     *       type、createdAt 它一个都用不上，却占了 70% 以上的 token</li>
     *   <li><b>字段名本身也在烧钱</b>。{@code imageUrl=null} 这种不但没信息，
     *       还占 5 个 token，30 条就是 150 个</li>
     * </ol>
     * <p>
     * 换成一行一条的管道分隔格式，格式说明只在开头写一次，
     * 不用每条都重复字段名。
     * <p>
     * 注意：这一步<b>只改表示，不改候选取哪些</b>，所以命中率不该变化，
     * 收益体现在 token 成本上。两个改动分开做，才能分别量化各自的效果。
     */
    private String formatCandidates(List<Item> candidates) {
        StringBuilder sb = new StringBuilder("候选物品（格式：id|标题|类别|地点|描述）：\n");
        for (Item item : candidates) {
            sb.append(item.getId()).append('|')
                    .append(nvl(item.getTitle())).append('|')
                    .append(nvl(item.getCategory())).append('|')
                    .append(nvl(item.getLocation())).append('|')
                    .append(truncate(nvl(item.getDescription()), DESC_MAX_LEN))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 防止某条描述特别长把 prompt 撑爆 */
    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 构建问答请求体 */
    private JSONObject buildChatBody(String question) {
        JSONObject body = new JSONObject();
        body.set("model", llmConfig.getModel());
        body.set("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", question)
        ));
        body.set("temperature", 0.7);
        body.set("max_tokens", llmConfig.getMaxTokens());
        return body;
    }

    /** 构建匹配请求体。temperature=0 是因为匹配要可复现，不能每次给出不同结果 */
    private JSONObject buildMatchBody(String prompt) {
        JSONObject body = new JSONObject();
        body.set("model", llmConfig.getModel());
        body.set("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        ));
        body.set("temperature", 0);
        body.set("response_format", Map.of("type", "json_object"));
        body.set("max_tokens", llmConfig.getMaxTokens());
        return body;
    }
}
