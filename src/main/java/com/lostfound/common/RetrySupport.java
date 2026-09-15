package com.lostfound.common;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 调用外部服务时的重试判断 —— 大模型和向量接口共用。
 * <p>
 * <b>为什么抽出来</b>：{@code AiServiceImpl} 里原本有一份私有的判断逻辑，
 * 接向量接口（P3）时需要一模一样的规则。复制一份的代价不是这 15 行代码，
 * 而是<b>两处会各自演进</b>：某天给向量接口加了「网络抖动也重试」，
 * 大模型那边忘了改，排查时你会发现同一个项目里有两套重试语义。
 * <p>
 * <b>核心原则：重试不是越多越好。</b>
 * 可恢复的错误（超时、5xx、429 限流）重试有意义；
 * 不可恢复的错误（401 密钥错、400 参数错、403 无权限）重试一万次也还是错，
 * 只会白白浪费时间和额度，还会把真正的配置问题掩盖成「服务不稳定」。
 */
public final class RetrySupport {

    private RetrySupport() {
    }

    /** 指数退避的基础等待时间（毫秒）：500 → 1000 → 2000 */
    private static final long BASE_BACKOFF_MS = 500L;

    /**
     * 判断这个异常值不值得重试。
     */
    public static boolean isRetryable(Exception e) {
        // 连接/读取超时、连接被重置 —— 免费档最常见的失败模式
        if (e instanceof ResourceAccessException) {
            return true;
        }
        // 5xx 服务端问题，通常是临时的
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        // 4xx 里只有 429 值得重试
        if (e instanceof HttpClientErrorException clientError) {
            return clientError.getStatusCode().value() == 429;
        }
        return false;
    }

    /**
     * 第 attempt 次失败后该等多久（attempt 从 0 开始）。
     * <p>
     * 指数退避 + 随机抖动。抖动是必要的：批量任务里 64 个请求同时超时，
     * 不加抖动就会在同一毫秒一起重试，把下游刚缓过来的服务再打挂一次
     * （这叫「重试风暴」，是雪崩的经典成因）。
     */
    public static long backoffMs(int attempt) {
        return BASE_BACKOFF_MS * (1L << attempt) + (long) (Math.random() * 200);
    }

    /** 睡眠，被中断时恢复中断标记而不是吞掉 —— 否则线程池关不掉 */
    public static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
