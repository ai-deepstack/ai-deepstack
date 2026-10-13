package org.deepstack.ai.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RunContext} 的安装 / 按 threadId 回查 / 绑定还原语义测试。
 * <p>
 * 图节点会被调度到安装线程之外的线程上，这层是"请求态还能不能取到"的唯一保障，
 * 因此把绑定还原（含异常路径）与索引注销都固定下来。
 * </p>
 */
class RunContextTest {

    @AfterEach
    void tearDown() {
        RunContext.clear();
    }

    @Test
    @DisplayName("install 后当前线程可取到，clear 后取不到")
    void installAndClear() {
        assertThat(RunContext.current()).isNull();
        RunContext.install("u1", "c1", "a1", "t1");
        assertThat(RunContext.current()).isNotNull();
        assertThat(RunContext.current().agentCode()).isEqualTo("a1");
        assertThat(RunContext.peekThreadId()).isEqualTo("t1");

        RunContext.clear();
        assertThat(RunContext.current()).isNull();
        assertThat(RunContext.peekThreadId()).isNull();
    }

    @Test
    @DisplayName("可按 threadId 回查，clear 后索引注销")
    void lookupByThreadId() {
        RunContext.install("u1", "c1", "a1", "t-lookup");
        assertThat(RunContext.forThread("t-lookup")).isNotNull();
        assertThat(RunContext.forThread("t-lookup").agentCode()).isEqualTo("a1");
        assertThat(RunContext.forThread("t-unknown")).isNull();
        assertThat(RunContext.forThread(null)).isNull();
        assertThat(RunContext.forThread("  ")).isNull();

        RunContext.clear();
        assertThat(RunContext.forThread("t-lookup")).isNull();
    }

    @Test
    @DisplayName("currentOrThread 当前线程优先，其次按 threadId")
    void currentOrThreadPrefersCurrentThread() {
        RunContext installed = RunContext.install("u1", "c1", "a1", "t1");
        assertThat(RunContext.currentOrThread("t-unknown")).isSameAs(installed);

        // 模拟"节点跑在别的线程"：当前线程没有绑定，只能靠 threadId 回查
        AtomicReference<RunContext> seen = new AtomicReference<>();
        Thread other = new Thread(() -> seen.set(RunContext.currentOrThread("t1")));
        other.start();
        joinQuietly(other);
        assertThat(seen.get()).isSameAs(installed);
    }

    @Test
    @DisplayName("bound 在无绑定的线程上临时绑定，结束后还原")
    void boundTemporarilySetsAndRestores() {
        RunContext installed = RunContext.install("u1", "c1", "a1", "t-bound");
        AtomicReference<RunContext> inside = new AtomicReference<>();
        AtomicReference<RunContext> after = new AtomicReference<>();

        Thread other = new Thread(() -> {
            assertThat(RunContext.current()).isNull();
            RunContext.bound(installed, () -> {
                inside.set(RunContext.current());
                return null;
            });
            after.set(RunContext.current());
        });
        other.start();
        joinQuietly(other);

        assertThat(inside.get()).isSameAs(installed);
        assertThat(after.get()).isNull();
    }

    @Test
    @DisplayName("bound 抛异常时也还原到调用前的绑定")
    void boundRestoresOnException() {
        RunContext outer = RunContext.install("u1", "c1", "a1", "t-outer");
        RunContext inner = RunContext.install("u2", "c2", "a2", "t-inner");
        assertThat(RunContext.current()).isSameAs(inner);

        assertThatThrownBy(() -> RunContext.bound(outer, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(RunContext.current()).isSameAs(inner);
    }

    @Test
    @DisplayName("重复 install 会摘掉上一份 threadId 索引")
    void reinstallDropsPreviousIndex() {
        RunContext.install("u1", "c1", "a1", "t-first");
        RunContext.install("u2", "c2", "a2", "t-second");

        assertThat(RunContext.forThread("t-first")).isNull();
        assertThat(RunContext.forThread("t-second")).isNotNull();
    }

    @Test
    @DisplayName("bound 传 null 不改变当前绑定")
    void boundWithNullKeepsCurrent() {
        RunContext installed = RunContext.install("u1", "c1", "a1", "t3");
        String out = RunContext.bound(null, () -> RunContext.current() == installed ? "same" : "changed");
        assertThat(out).isEqualTo("same");
    }

    @Test
    @DisplayName("嵌套 bound 还原到上一层而不是清空")
    void nestedBoundRestoresOuter() {
        RunContext outer = RunContext.install("u1", "c1", "a1", "t-outer");
        RunContext inner = RunContext.install("u2", "c2", "a2", "t-inner");
        AtomicReference<RunContext> seen = new AtomicReference<>();

        Thread other = new Thread(() -> RunContext.bound(outer, () -> {
            RunContext.bound(inner, () -> {
                seen.set(RunContext.current());
                return null;
            });
            seen.set(RunContext.current());
            return null;
        }));
        other.start();
        joinQuietly(other);

        assertThat(seen.get()).isSameAs(outer);
    }

    @Test
    @DisplayName("待确认卡片可跨线程收集并一次性取出")
    void pendingCardsCollectedAcrossThreads() {
        RunContext ctx = RunContext.install("u1", "c1", "a1", "t-card");
        Thread t1 = new Thread(() -> ctx.addPendingCard(Map.of("cardId", "c-1")));
        Thread t2 = new Thread(() -> ctx.addPendingCard(Map.of("cardId", "c-2")));
        t1.start();
        t2.start();
        joinQuietly(t1);
        joinQuietly(t2);

        List<Map<String, Object>> taken = ctx.takePendingCards();
        assertThat(taken).hasSize(2);
        assertThat(taken).extracting(m -> m.get("cardId")).containsExactlyInAnyOrder("c-1", "c-2");
        // 取出后再取为空，且空卡片不入队
        assertThat(ctx.takePendingCards()).isEmpty();
        ctx.addPendingCard(Map.of());
        assertThat(ctx.takePendingCards()).isEmpty();
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
