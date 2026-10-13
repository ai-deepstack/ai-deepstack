package org.deepstack.ai.agent.observability;

import org.deepstack.ai.settings.SysConfigPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TrajectoryRedactor} 的遮盖与截断测试。
 * <p>
 * 这层是"密钥不进库"的最后一道闸，正则漏掉一种 Key 形态就等于静默泄露，
 * 因此把常见凭据形态与截断行为都固定下来。
 * </p>
 */
class TrajectoryRedactorTest {

    /** 可控开关的 SysConfigPort 桩；getInt 一律回落到调用方默认值。 */
    private static SysConfigPort config(boolean enabled) {
        return new SysConfigPort() {
            @Override
            public String getString(String key) {
                return null;
            }

            @Override
            public int getInt(String key) {
                return 0;
            }

            @Override
            public int getInt(String key, int defaultValue) {
                return defaultValue;
            }

            @Override
            public double getDouble(String key) {
                return 0;
            }

            @Override
            public boolean isYes(String key) {
                return enabled;
            }
        };
    }

    private static TrajectoryRedactor enabled() {
        return new TrajectoryRedactor(config(true));
    }

    @Test
    @DisplayName("关闭脱敏时原样返回")
    void disabledReturnsInputUnchanged() {
        TrajectoryRedactor redactor = new TrajectoryRedactor(config(false));
        String raw = "联系 13800138000，key=sk-abcdefgh12345678";
        assertThat(redactor.redactText(raw, 0)).isEqualTo(raw);
        assertThat(redactor.redactErrorMessage(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("手机号被遮盖")
    void phoneMasked() {
        String out = enabled().redactText("联系 13800138000 谢谢", 0);
        assertThat(out).contains("***PHONE***").doesNotContain("13800138000");
    }

    @Test
    @DisplayName("更长数字串中的手机号不误伤")
    void phoneInsideLongerDigitsNotMasked() {
        // 12 位连续数字不是手机号，边界断言应让它原样保留
        String out = enabled().redactText("流水号 138001380001", 0);
        assertThat(out).doesNotContain("***PHONE***");
    }

    @Test
    @DisplayName("常见 API Key 形态都被遮盖")
    void apiKeysMasked() {
        TrajectoryRedactor r = enabled();
        // OpenAI 直连、项目级、live/test 变体
        assertThat(r.redactText("k=sk-abcdefgh12345678", 0)).doesNotContain("sk-abcdefgh12345678");
        assertThat(r.redactText("k=sk-proj-abcdefgh12345678", 0)).doesNotContain("sk-proj-abcdefgh12345678");
        assertThat(r.redactText("k=sk_live_abcdefgh12345678", 0)).doesNotContain("sk_live_abcdefgh12345678");
        assertThat(r.redactText("k=pk_test_abcdefgh12345678", 0)).doesNotContain("pk_test_abcdefgh12345678");
    }

    @Test
    @DisplayName("AWS / GitHub / Slack 凭据被遮盖")
    void otherCloudCredentialsMasked() {
        TrajectoryRedactor r = enabled();
        assertThat(r.redactText("id=AKIAIOSFODNN7EXAMPLE", 0)).contains("***API_KEY***");
        assertThat(r.redactText("t=ghp_abcdefghijklmnopqrstuvwxyz0123456789", 0)).contains("***API_KEY***");
        assertThat(r.redactText("t=xoxb-123456789012-abcdefghijkl", 0)).contains("***API_KEY***");
    }

    @Test
    @DisplayName("普通词不会被误判成密钥")
    void ordinaryWordsUntouched() {
        String out = enabled().redactText("task-list 与 risk-free 都是普通词", 0);
        assertThat(out).doesNotContain("***API_KEY***");
        assertThat(out).isEqualTo("task-list 与 risk-free 都是普通词");
    }

    @Test
    @DisplayName("Bearer 头被遮盖")
    void bearerMasked() {
        String out = enabled().redactText("Authorization: Bearer abc.def.ghi", 0);
        assertThat(out).contains("Bearer ***TOKEN***").doesNotContain("abc.def.ghi");
    }

    @Test
    @DisplayName("超长文本按上限截断并加后缀")
    void truncationAppendsSuffix() {
        String out = enabled().redactText("x".repeat(100), 10);
        assertThat(out).startsWith("xxxxxxxxxx").endsWith("…(truncated)");
    }

    @Test
    @DisplayName("错误信息同样脱敏")
    void errorMessageRedacted() {
        String out = enabled().redactErrorMessage(
                "call failed: Authorization: Bearer sk-abcdefgh12345678, phone 13800138000");
        assertThat(out).doesNotContain("sk-abcdefgh12345678").doesNotContain("13800138000");
    }

    @Test
    @DisplayName("节点时间线只处理 input/output/error 等敏感字段")
    void nodeExecutionsRedactedSelectively() {
        Object out = enabled().redactNodeExecutions(List.of(Map.of(
                "nodeId", "n1",
                "input", "电话 13800138000",
                "errorMessage", "bad key sk-proj-abcdefgh12345678",
                "status", "SUCCESS")));

        assertThat(out).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) ((List<?>) out).get(0);
        assertThat(node.get("nodeId")).isEqualTo("n1");
        assertThat(node.get("status")).isEqualTo("SUCCESS");
        assertThat(String.valueOf(node.get("input"))).contains("***PHONE***");
        assertThat(String.valueOf(node.get("errorMessage"))).contains("***API_KEY***");
    }

    @Test
    @DisplayName("stages 保留数值、截断长字符串")
    void stagesKeepNumbersTruncateStrings() {
        Object out = enabled().redactStages(Map.of("llmMs", 123, "note", "y".repeat(200)));

        @SuppressWarnings("unchecked")
        Map<String, Object> stages = (Map<String, Object>) out;
        assertThat(stages.get("llmMs")).isEqualTo(123);
        assertThat(String.valueOf(stages.get("note"))).hasSize(64 + "…(truncated)".length());
    }

    @Test
    @DisplayName("null 输入原样返回")
    void nullSafe() {
        TrajectoryRedactor r = enabled();
        assertThat(r.redactText(null, 10)).isNull();
        assertThat(r.redactErrorMessage(null)).isNull();
        assertThat(r.redactNodeExecutions(null)).isNull();
        assertThat(r.redactStages(null)).isNull();
    }
}
