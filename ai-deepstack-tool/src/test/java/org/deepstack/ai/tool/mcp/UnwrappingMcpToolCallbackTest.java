package org.deepstack.ai.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * {@link UnwrappingMcpToolCallback} 单元测试。
 *
 * <p>不引入 Mockito：实现 {@link ToolCallback} 接口固定返回值即可覆盖全部分支。</p>
 */
class UnwrappingMcpToolCallbackTest {

	/** 返回预置字符串的 stub。 */
	private static ToolCallback stubOf(String value, ToolDefinition definition) {
		return new ToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return definition;
			}

			@Override
			public String call(String input) {
				return value;
			}

			@Override
			public String call(String input, ToolContext context) {
				return value;
			}
		};
	}

	private UnwrappingMcpToolCallback wrap(String value) {
		return new UnwrappingMcpToolCallback(stubOf(value, null));
	}

	@Test
	void unwrapsSingleTextContent() {
		String result = wrap("[{\"type\":\"text\",\"text\":\"{\\\"ok\\\":true,\\\"type\\\":\\\"bazi\\\"}\"}]")
				.call("{}");
		assertThat(result).isEqualTo("{\"ok\":true,\"type\":\"bazi\"}");
	}

	@Test
	void unwrapsMultipleTextContentsInOrder() {
		String result = wrap("[{\"type\":\"text\",\"text\":\"first\"},{\"type\":\"text\",\"text\":\"second\"}]")
				.call("{}");
		assertThat(result).isEqualTo("first\nsecond");
	}

	@Test
	void nonTextContentIsIgnored() {
		String result = wrap("[{\"type\":\"image\",\"data\":\"AAAA\"},{\"type\":\"text\",\"text\":\"kept\"}]")
				.call("{}");
		assertThat(result).isEqualTo("kept");
	}

	@Test
	void keepsChineseAndNestedJson() {
		String result = wrap("[{\"type\":\"text\",\"text\":\"{\\\"hexagram\\\":{\\\"name\\\":\\\"天泽履\\\"}}\"}]")
				.call("{}");
		assertThat(result).isEqualTo("{\"hexagram\":{\"name\":\"天泽履\"}}");
	}

	@Test
	void returnsRawWhenNotContentArray() {
		String raw = "{\"ok\":true}";
		assertThat(wrap(raw).call("{}")).isEqualTo(raw);
	}

	@Test
	void returnsRawWhenParseFails() {
		String raw = "[{\"type\":\"text\",\"text\":broken";
		assertThat(wrap(raw).call("{}")).isEqualTo(raw);
	}

	@Test
	void returnsNullForNullUpstream() {
		assertThat(wrap(null).call("{}")).isNull();
	}

	@Test
	void unwrapsWhenToolContextPresent() {
		String callback = wrap("[{\"type\":\"text\",\"text\":\"ctx-ok\"}]")
				.call("{}", new ToolContext(java.util.Map.of("k", "v")));
		assertThat(callback).isEqualTo("ctx-ok");
	}

	@Test
	void delegatesToolDefinition() {
		ToolDefinition definition = new ToolDefinition() {
			@Override
			public String name() {
				return "probe";
			}

			@Override
			public String description() {
				return "";
			}

			@Override
			public String inputSchema() {
				return "{}";
			}
		};
		assertThat(stubOf("", definition).getToolDefinition()).isSameAs(definition);
	}
}
