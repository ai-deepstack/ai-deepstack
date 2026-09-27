package org.deepstack.ai.tool.mcp;

import org.deepstack.ai.kernel.enums.common.YesNo;
import org.deepstack.ai.settings.SysConfigKeys;
import org.deepstack.ai.settings.SysConfigPort;
import org.deepstack.ai.tool.mcp.model.entity.AiMcpConnection;
import org.deepstack.ai.tool.mcp.service.AiMcpConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动后连接已启用的 MCP Server 并同步工具目录。
 * <p>
 * 有界并发建连；单条失败只记 warn，不阻断应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpConnectionBootstrap {

    private static final int DEFAULT_MAX_CONCURRENCY = 4;

    private final SysConfigPort sysConfigPort;
    private final AiMcpConnectionService mcpConnectionService;

    /**
     * 应用就绪后：若 MCP 开启，则对所有启用连接并行 {@code connectAndSync}。
     */
    @Order(200)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!sysConfigPort.isYes(SysConfigKeys.MCP_ENABLED)) {
            log.info("MCP disabled (mcp.enabled=0), skip bootstrap");
            return;
        }
        List<AiMcpConnection> list = mcpConnectionService.lambdaQuery()
                .eq(AiMcpConnection::getEnabled, YesNo.YES.getCode())
                .list();
        if (list == null || list.isEmpty()) {
            log.info("MCP bootstrap: no enabled connections");
            return;
        }
        String prefix = sysConfigPort.getString(SysConfigKeys.MCP_TOOL_CODE_PREFIX);
        int maxConc = Math.max(1, Math.min(list.size(),
                sysConfigPort.getInt(SysConfigKeys.MCP_BOOTSTRAP_MAX_CONCURRENCY, DEFAULT_MAX_CONCURRENCY)));
        log.info("MCP bootstrap: connecting {} enabled connection(s), concurrency={}, toolCodePrefix={}",
                list.size(), maxConc, prefix);

        Semaphore lim = new Semaphore(maxConc);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(maxConc, r -> {
            Thread t = new Thread(r, "mcp-boot-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(list.size());
            for (AiMcpConnection c : list) {
                futures.add(CompletableFuture.runAsync(() -> {
                    lim.acquireUninterruptibly();
                    try {
                        int n = mcpConnectionService.connectAndSync(c.getId());
                        ok.incrementAndGet();
                        log.info("MCP bootstrap: connected code={}, tools={}", c.getConnectionCode(), n);
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        log.warn("MCP bootstrap: failed code={}: {}",
                                c.getConnectionCode(), e.getMessage(), e);
                    } finally {
                        lim.release();
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }
        }
        log.info("MCP bootstrap: finished ok={}, fail={}", ok.get(), fail.get());
    }
}
