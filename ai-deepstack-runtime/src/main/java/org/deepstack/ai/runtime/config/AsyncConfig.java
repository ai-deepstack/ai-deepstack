package org.deepstack.ai.runtime.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 * <p>
 * 提供 {@code knowledgeProcessExecutor} 用于知识库文档解析 + embedding 流水线。
 * 与默认 {@code taskExecutor} 隔离，避免长任务阻塞业务线程。
 * 同时配置 Spring MVC 异步请求处理线程池，消除 SimpleAsyncTaskExecutor 警告。
 * </p>
 *
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer, WebMvcConfigurer {

    /**
     * 知识库处理线程池：核心 2 / 最大 8 / 队列 200
     */
    @Bean("knowledgeProcessExecutor")
    public Executor knowledgeProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("kb-proc-");
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：调用方运行（保证任务不丢，但会阻塞业务线程）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new org.deepstack.ai.kernel.observability.MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * 知识库召回线程池：向量/全文并发与图谱多种子 expand。
     * <p>
     * 与 {@link #knowledgeProcessExecutor()} 隔离，避免建图/解析长任务占满召回线程。
     * 核心 4 / 最大 16 / 队列 64。
     * </p>
     */
    @Bean("knowledgeRecallExecutor")
    public Executor knowledgeRecallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("kb-recall-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new org.deepstack.ai.kernel.observability.MdcTaskDecorator());
        executor.initialize();
        log.info("knowledgeRecallExecutor 已初始化: core=4, max=16, queue=64");
        return executor;
    }

    /**
     * 多库召回编排线程池：只跑「按库提交 + 等待」，不跑 JDBC/embedding。
     * <p>
     * 必须与 {@link #knowledgeRecallExecutor()} 分离，否则
     * {@code retrieveAll → supplyAsync(retrieveOne) → hybridSearch 再 supplyAsync+join}
     * 会在同一池上嵌套等待，池满时死锁。
     * 核心 2 / 最大 8 / 队列 32。
     * </p>
     */
    @Bean("knowledgeMultiKbExecutor")
    public Executor knowledgeMultiKbExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("kb-multikb-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new org.deepstack.ai.kernel.observability.MdcTaskDecorator());
        executor.initialize();
        log.info("knowledgeMultiKbExecutor 已初始化: core=2, max=8, queue=32");
        return executor;
    }

    /**
     * CHAT 拼 system prompt 专用池：画像 / RAG / 记忆三段并行。
     * <p>
     * 禁止复用 {@link #knowledgeMultiKbExecutor()}：RAG 任务线程内会再往多库编排池
     * {@code supplyAsync}，同池嵌套等待在池满时会死锁。
     * 核心 2 / 最大 8 / 队列 32。
     * </p>
     */
    @Bean("chatContextExecutor")
    public Executor chatContextExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("chat-ctx-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new org.deepstack.ai.kernel.observability.MdcTaskDecorator());
        executor.initialize();
        log.info("chatContextExecutor 已初始化: core=2, max=8, queue=32");
        return executor;
    }

    /**
     * 召回 embedding 专用池：与 {@link #knowledgeRecallExecutor()} 分离。
     * <p>
     * 向量路在召回线程上 {@code get(timeout)} 等待本池。超时后召回线程立即返回；
     * HTTP 客户端未必响应中断，因此用有界池把卡住的 embedding 调用隔离开。
     * 拒绝策略为 Abort，避免调用方线程自己跑 embedding 再 {@code get} 造成死锁。
     * 核心 2 / 最大 4 / 队列 16。
     * </p>
     */
    @Bean("knowledgeEmbedExecutor")
    public Executor knowledgeEmbedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("kb-embed-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setTaskDecorator(new org.deepstack.ai.kernel.observability.MdcTaskDecorator());
        executor.initialize();
        log.info("knowledgeEmbedExecutor 已初始化: core=2, max=4, queue=16");
        return executor;
    }

    /**
     * Spring MVC 异步请求处理线程池（SSE / Flux 流式响应使用）
     * <p>
     * 不配置时 Spring MVC 默认用 SimpleAsyncTaskExecutor（每次请求新建线程），
     * 生产环境下会导致线程爆炸和性能问题。
     * </p>
     */
    @Bean("mvcTaskExecutor")
    public ThreadPoolTaskExecutor mvcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new org.deepstack.ai.kernel.observability.MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * 工作流执行线程池：用于 AgentWorkflow 流式执行（CompletableFuture.runAsync）。
     * <p>
     * 独立于 ForkJoinPool.commonPool()，避免工作流长任务占满共享池导致所有异步请求阻塞。
     * 核心 4 / 最大 32 / 队列 50，CallerRunsPolicy 保证任务不丢。
     * </p>
     */
    @Bean("workflowExecutor")
    public ThreadPoolTaskExecutor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("wf-exec-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("Workflow executor rejected task (pool full, queue full): active={}, queue={}",
                    e.getPoolSize(), e.getQueue().size());
            throw new java.util.concurrent.RejectedExecutionException(
                    "Workflow executor saturated: too many concurrent workflow executions");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setTaskDecorator(new org.deepstack.ai.kernel.observability.MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * 将 MVC 异步请求绑定到 {@link #mvcTaskExecutor()}。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcTaskExecutor());
        // SSE 流式对话可能持续较长时间，超时设为 5 分钟
        configurer.setDefaultTimeout(300_000L);
    }

    /**
     * @Async 默认执行器：知识库处理线程池。
     */
    @Override
    public Executor getAsyncExecutor() {
        return knowledgeProcessExecutor();
    }
}
