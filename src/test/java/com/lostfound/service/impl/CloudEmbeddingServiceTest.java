package com.lostfound.service.impl;

import com.lostfound.config.EmbeddingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CloudEmbeddingService 测试。
 *
 * <b>这个测试会真的调云端接口，所以默认跳过</b> ——
 * 它需要 key，而且会产生网络调用，不适合放进自动跑的 {@code mvn test}。
 * 想跑的话：
 * <pre>
 *   EMBEDDING_API_KEY=sk-xxx mvn test -Dtest=CloudEmbeddingServiceTest
 * </pre>
 * 没有 key 时用 {@code assumeTrue} 跳过而不是失败 —— 测试失败是有信息量的信号，
 * 「没配 key」不该占用这个信号。
 *
 * <b>为什么不用 @SpringBootTest</b>：那会连带启动数据源、Redis、拦截器，
 * 一个只关心 HTTP 调用的测试要依赖 MySQL 能连上，跑起来又慢又脆。
 * 直接 new 出被测对象，依赖手写 —— 和项目里其它单测保持一致。
 */
class CloudEmbeddingServiceTest {

    private CloudEmbeddingService service;

    @BeforeEach
    void setUp() {
        String key = System.getenv("EMBEDDING_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "未设置 EMBEDDING_API_KEY，跳过云端向量化测试");

        EmbeddingConfig config = new EmbeddingConfig();
        config.setProvider("cloud");
        config.setApiUrl("https://api.siliconflow.cn/v1/embeddings");
        config.setApiKey(key);
        config.setModel("BAAI/bge-m3");
        config.setDimension(1024);
        config.setConnectTimeout(3);
        config.setReadTimeout(10);
        config.setMaxRetries(2);

        service = new CloudEmbeddingService(config, config.embeddingRestTemplate());
    }

    @Test
    @DisplayName("空文本整批失败，不做静默跳过")
    void blankTextFailsWholeBatch() {
        // 这条不花网络请求，即使没 key 也值得跑 —— 所以用 assert 而不是 assume
        EmbeddingConfig config = new EmbeddingConfig();
        config.setDimension(1024);
        CloudEmbeddingService svc = new CloudEmbeddingService(config, config.embeddingRestTemplate());

        assertNull(svc.embedBatch(List.of("正常文本", "  ", "另一条")),
                "含空文本时应整批返回 null —— 静默跳过会让结果和入参下标错位");
        assertNull(svc.embed("   "), "空文本单条也应失败");
    }

    @Test
    @DisplayName("语义验证：字面零重合的同义词能被排到第一")
    void semanticMatchBeatsLiteralOverlap() {
        String query = "有没有人看到我的本子啊，蓝色的那种";
        List<String> candidates = List.of(
                "捡到一本蓝色笔记本，在教三 302",
                "捡到一台蓝色笔记本电脑，带充电器",
                "捡到一把蓝色雨伞");

        List<float[]> vectors = service.embedBatch(concat(query, candidates));
        assertNotNull(vectors, "向量化失败（看日志里的报错，多半是 key 或额度问题）");
        assertEquals(4, vectors.size());

        float[] q = vectors.get(0);
        double toNotebook = cosine(q, vectors.get(1));
        double toLaptop = cosine(q, vectors.get(2));
        double toUmbrella = cosine(q, vectors.get(3));

        System.out.printf("本子↔笔记本 %.4f | 本子↔笔记本电脑 %.4f | 本子↔雨伞 %.4f%n",
                toNotebook, toLaptop, toUmbrella);

        assertTrue(toNotebook > toLaptop,
                "「笔记本」应该比「笔记本电脑」更接近「本子」—— 2-gram 做不到这件事，这正是 P3 的价值");
        assertTrue(toLaptop > toUmbrella, "笔记本/笔记本电脑 应该都比雨伞更像本子");
    }

    @Test
    @DisplayName("批量分批：5 条、batchSize=2 时应发 3 次请求并保持顺序")
    void batchSplitsAndKeepsOrder() {
        // 把 batchSize 调到 2，逼出分批逻辑（默认 64 的话 5 条只会发 1 次，测不到）
        EmbeddingConfig config = new EmbeddingConfig();
        config.setApiUrl("https://api.siliconflow.cn/v1/embeddings");
        config.setApiKey(System.getenv("EMBEDDING_API_KEY"));
        config.setModel("BAAI/bge-m3");
        config.setDimension(1024);
        config.setBatchSize(2);
        config.setConnectTimeout(3);
        config.setReadTimeout(10);
        CloudEmbeddingService svc = new CloudEmbeddingService(config, config.embeddingRestTemplate());

        List<String> texts = List.of("甲", "乙", "丙", "丁", "戊");
        List<float[]> vectors = svc.embedBatch(texts);

        assertNotNull(vectors, "分批调用失败");
        assertEquals(5, vectors.size(), "5 条输入应返回 5 条向量");
        for (float[] v : vectors) {
            assertEquals(1024, v.length, "维度应为配置里的 1024");
        }

        // 顺序校验：同一段文本单独算一次，应该和批量里对应位置的结果一致。
        // 这是防「返回顺序错位」的核心断言 —— 错位不会报错，只会让匹配结果莫名变烂。
        float[] jiaAlone = svc.embed("甲");
        assertNotNull(jiaAlone);
        assertEquals(cosine(jiaAlone, vectors.get(0)), 1.0, 1e-6,
                "批量返回的第 0 条必须对应入参第 0 条「甲」");
    }

    // ===================== 辅助 =====================

    private static List<String> concat(String head, List<String> rest) {
        java.util.ArrayList<String> all = new java.util.ArrayList<>();
        all.add(head);
        all.addAll(rest);
        return all;
    }

    /** 余弦相似度 —— 和 P4 召回层要用的算法是同一个 */
    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
